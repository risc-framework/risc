#include "demu/hal/bus/axil/irh/clint.hh"

namespace demu::hal::axil {

void AXILiteCLINT::reset() {
  allocator_->clear();
  _write_addr_queue = {};
  _write_data_queue = {};
  _write_resp_queue = {};
  _read_queue = {};

  if (timer_line_) {
    timer_line_->deassert_line();
  }
  if (soft_line_) {
    soft_line_->deassert_line();
  }
}

void AXILiteCLINT::clock_tick() {
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

  process_writes();
  process_reads();
}

void AXILiteCLINT::process_writes() {
  if (_write_addr_queue.empty() || _write_data_queue.empty()) {
    return;
  }

  const addr_t addr = _write_addr_queue.front();
  _write_addr_queue.pop();
  const WriteData wdata = _write_data_queue.front();
  _write_data_queue.pop();

  const bool valid = owns_address(addr);

  if (valid) {
    for (int i = 0; i < 4; ++i) {
      if (wdata.strb & (1u << i)) {
        allocator()->write_byte(
            addr + i, static_cast<byte_t>((wdata.data >> (i * 8)) & 0xFF));
      }
    }

    addr_t offset = to_offset(addr);
    if (offset == CLINT_MSIP) {
      word_t msip = allocator_->read_word(base_address() + CLINT_MSIP);
      allocator_->write_word(base_address() + CLINT_MSIP, msip & 1);
    }
  }

  _write_resp_queue.push(
      {valid ? static_cast<uint8_t>(0) : static_cast<uint8_t>(2)});
}

void AXILiteCLINT::process_reads() {
  if (_read_queue.empty() || _read_queue.front().processed) {
    return;
  }

  ReadTransaction &rt = _read_queue.front();
  const bool valid = owns_address(rt.addr);

  rt.data = valid ? allocator()->read_word(rt.addr) : 0u;
  rt.processed = true;
}

void AXILiteCLINT::dump(addr_t start, size_t size) const noexcept {
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

} // namespace demu::hal::axil
