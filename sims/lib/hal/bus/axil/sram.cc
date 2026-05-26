#include "demu/hal/bus/axil/sram.hh"
#include <iomanip>
#include <sstream>

namespace demu::hal::axil {

void AXILiteSRAM::reset() {
  sram_->reset();
  _write_addr_queue = {};
  _write_data_queue = {};
  _write_resp_queue = {};
  _read_queue = {};
}

void AXILiteSRAM::clock_tick() {
  process_writes();
  process_reads();
}

void AXILiteSRAM::process_writes() {
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
  }

  _write_resp_queue.push(
      {valid ? static_cast<uint8_t>(0) : static_cast<uint8_t>(2)});
}

void AXILiteSRAM::process_reads() {
  if (_read_queue.empty() || _read_queue.front().processed) {
    return;
  }

  ReadTransaction &rt = _read_queue.front();
  const bool valid = owns_address(rt.addr);

  rt.data = valid ? allocator()->read_word(rt.addr) : 0u;
  rt.processed = true;
}

void AXILiteSRAM::dump(addr_t start, size_t size) const noexcept {
  if (!owns_address(start)) {
    HAL_WARN("AXILiteSRAM dump: 0x{:08X} not owned",
             static_cast<uint64_t>(start));
    return;
  }
  const size_t clamped = std::min(
      size, static_cast<size_t>(base_address() + address_range() - start));

  HAL_INFO("SRAM dump [0x{:08X} - 0x{:08X}]:", static_cast<uint64_t>(start),
           static_cast<uint64_t>(start + clamped));

  const byte_t *ptr = allocator()->get_ptr(start);
  if (!ptr) {
    return;
  }

  for (size_t i = 0; i < clamped; i += 16) {
    std::stringstream ss;
    ss << std::hex << std::setw(8) << std::setfill('0') << (start + i) << ": ";
    for (size_t j = 0; j < 16; ++j) {
      if (i + j < clamped) {
        ss << std::hex << std::setw(2) << std::setfill('0')
           << static_cast<int>(ptr[i + j]) << ' ';
      } else {
        ss << "   ";
      }
      if (j == 7) {
        ss << ' ';
      }
    }
    ss << " |";
    for (size_t j = 0; j < 16 && (i + j) < clamped; ++j) {
      const byte_t c = ptr[i + j];
      ss << static_cast<char>((c >= 32 && c < 127) ? c : '.');
    }
    ss << '|';
    HAL_INFO("{}", ss.str());
  }
}

} // namespace demu::hal::axil
