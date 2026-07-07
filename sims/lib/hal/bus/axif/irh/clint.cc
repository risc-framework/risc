#include "demu/hal/bus/axif/irh/clint.hh"

namespace demu::hal::axif {

void AXIFullCLINT::reset() {
  allocator_->clear();

  allocator_->write_word(base_address() + CLINT_MTIMECMP_LO, 0xFFFFFFFF);
  allocator_->write_word(base_address() + CLINT_MTIMECMP_HI, 0xFFFFFFFF);

  _write_req_queue = std::queue<BurstTransaction>();
  _write_data_queue = std::queue<WriteData>();
  _write_resp_queue = std::queue<WriteResponse>();
  _read_req_queue = std::queue<BurstTransaction>();
  _read_data_queue = std::queue<ReadData>();

  pin_awvalid = false;
  pin_wvalid = false;
  pin_bready = false;
  pin_arvalid = false;
  pin_rready = false;

  if (timer_line_) {
    timer_line_->deassert_line();
  }
  if (soft_line_) {
    soft_line_->deassert_line();
  }
}

void AXIFullCLINT::clock_tick() {
  const addr_t base = base_address();

  uint32_t mtime_lo = allocator_->read_word(base + CLINT_MTIME_LO);
  uint32_t mtime_hi = allocator_->read_word(base + CLINT_MTIME_HI);
  uint64_t mtime = (static_cast<uint64_t>(mtime_hi) << 32) | mtime_lo;

  // RTC
  mtime += TICK_NS_DIVIDER / freq_; // NOTE: in ns
  allocator_->write_word(base + CLINT_MTIME_LO,
                         static_cast<uint32_t>(mtime & 0xFFFFFFFF));
  allocator_->write_word(base + CLINT_MTIME_HI,
                         static_cast<uint32_t>(mtime >> 32));

  if (timer_line_ || soft_line_) {
    uint32_t mtimecmp_lo = allocator_->read_word(base + CLINT_MTIMECMP_LO);
    uint32_t mtimecmp_hi = allocator_->read_word(base + CLINT_MTIMECMP_HI);
    uint64_t mtimecmp =
        (static_cast<uint64_t>(mtimecmp_hi) << 32) | mtimecmp_lo;

    uint32_t msip = allocator_->read_word(base + CLINT_MSIP);

    if (timer_line_) {
      timer_line_->set_level(mtime >= mtimecmp);
    }
    if (soft_line_) {
      soft_line_->set_level((msip & 1) != 0);
    }
  }

  if (pin_awvalid && aw_ready()) {
    _write_req_queue.push(
        {pin_awid, pin_awaddr, pin_awlen, pin_awsize, pin_awburst, 0});
  }
  if (pin_wvalid && w_ready()) {
    _write_data_queue.push({pin_wdata, pin_wstrb, pin_wlast});
  }
  if (pin_bready && b_valid()) {
    _write_resp_queue.pop();
  }
  if (pin_arvalid && ar_ready()) {
    _read_req_queue.push(
        {pin_arid, pin_araddr, pin_arlen, pin_arsize, pin_arburst, 0});
  }
  if (pin_rready && r_valid()) {
    _read_data_queue.pop();
  }

  process_writes();
  process_reads();
}

void AXIFullCLINT::calculate_next_address(BurstTransaction &req) {
  if (req.burst == 1 || req.burst == 2) {
    req.addr += (1u << req.size);
  }
}

void AXIFullCLINT::process_writes() {
  if (_write_req_queue.empty() || _write_data_queue.empty()) {
    return;
  }

  BurstTransaction &req = _write_req_queue.front();
  const WriteData wdata = _write_data_queue.front();
  _write_data_queue.pop();

  const bool valid = owns_address(req.addr);

  if (valid) {
    for (int i = 0; i < 4; ++i) {
      if (wdata.strb & (1u << i)) {
        allocator_->write_byte(
            req.addr + i, static_cast<byte_t>((wdata.data >> (i * 8)) & 0xFF));
      }
    }

    addr_t offset = to_offset(req.addr);
    if (offset == CLINT_MSIP) {
      word_t msip = allocator_->read_word(base_address() + CLINT_MSIP);
      allocator_->write_word(base_address() + CLINT_MSIP, msip & 1);
    }
  }

  req.beats++;
  calculate_next_address(req);

  if (wdata.last || req.beats > req.len) {
    _write_resp_queue.push(
        {req.id,
         static_cast<uint8_t>(valid ? 0 : 2)}); // OKAY (0) or SLVERR (2)
    _write_req_queue.pop();
  }
}

void AXIFullCLINT::process_reads() {
  if (_read_req_queue.empty()) {
    return;
  }

  BurstTransaction &req = _read_req_queue.front();
  const bool valid = owns_address(req.addr);

  word_t data = valid ? allocator_->read_word(req.addr) : 0u;

  const bool last = (req.beats == req.len);
  _read_data_queue.push(
      {req.id, data, static_cast<uint8_t>(valid ? 0 : 2), last});

  req.beats++;
  calculate_next_address(req);

  if (last) {
    _read_req_queue.pop();
  }
}

void AXIFullCLINT::dump(addr_t start, size_t size) const noexcept {
  const addr_t base = base_address();
  const addr_t end = start + size;

  auto overlaps = [&](addr_t reg_start, size_t reg_size) -> bool {
    return reg_start < end && (reg_start + reg_size) > start;
  };

  bool printed_header = false;
  auto print_header_once = [&]() {
    if (!printed_header) {
      HAL_INFO("--- CLINT Registers Dump [0x{:08X} - 0x{:08X}] ---", start,
               end);
      printed_header = true;
    }
  };

  if (overlaps(base + CLINT_MSIP, 4)) {
    print_header_once();
    uint32_t msip = allocator_->read_word(base + CLINT_MSIP);
    HAL_INFO("  MSIP     : 0x{:08x}", msip);
  }

  if (overlaps(base + CLINT_MTIMECMP_LO, 8)) {
    print_header_once();
    uint32_t mtimecmp_lo = allocator_->read_word(base + CLINT_MTIMECMP_LO);
    uint32_t mtimecmp_hi = allocator_->read_word(base + CLINT_MTIMECMP_HI);
    uint64_t mtimecmp =
        (static_cast<uint64_t>(mtimecmp_hi) << 32) | mtimecmp_lo;
    HAL_INFO("  MTIMECMP : 0x{:016x}", mtimecmp);
  }

  if (overlaps(base + CLINT_MTIME_LO, 8)) {
    print_header_once();
    uint32_t mtime_lo = allocator_->read_word(base + CLINT_MTIME_LO);
    uint32_t mtime_hi = allocator_->read_word(base + CLINT_MTIME_HI);
    uint64_t mtime = (static_cast<uint64_t>(mtime_hi) << 32) | mtime_lo;
    HAL_INFO("  MTIME    : 0x{:016x}", mtime);
  }

  if (printed_header) {
    HAL_INFO("--------------------------------------------------");
  }
}

} // namespace demu::hal::axif
