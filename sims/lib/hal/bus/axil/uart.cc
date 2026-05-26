#include "demu/hal/bus/axil/uart.hh"

namespace demu::hal::axil {

void AXILiteUART::reset() {
  uart_->reset();
  _write_addr_queue = {};
  _write_data_queue = {};
  _write_resp_queue = {};
  _read_queue = {};
}

void AXILiteUART::clock_tick() {
  process_writes();
  process_reads();
}

void AXILiteUART::process_writes() {
  if (_write_addr_queue.empty() || _write_data_queue.empty()) {
    return;
  }

  const addr_t addr = _write_addr_queue.front();
  _write_addr_queue.pop();
  const WriteData wdata = _write_data_queue.front();
  _write_data_queue.pop();

  const bool valid = owns_address(addr);

  if (valid) {
    addr_t offset = to_offset(addr);

    if (offset == uart::UART_TXD) {
      char c = static_cast<char>(wdata.data & 0xFF);
      std::cout << c << std::flush;
    } else {
      for (int i = 0; i < 4; ++i) {
        if (wdata.strb & (1u << i)) {
          allocator()->write_byte(
              addr + i, static_cast<byte_t>((wdata.data >> (i * 8)) & 0xFF));
        }
      }
    }
  }

  _write_resp_queue.push(
      {valid ? static_cast<uint8_t>(0) : static_cast<uint8_t>(2)});
}

void AXILiteUART::process_reads() {
  if (_read_queue.empty() || _read_queue.front().processed) {
    return;
  }

  ReadTransaction &rt = _read_queue.front();
  const bool valid = owns_address(rt.addr);

  rt.data = valid ? allocator()->read_word(rt.addr) : 0u;
  rt.processed = true;
}

void AXILiteUART::dump(addr_t start, size_t size) const noexcept {
  uart_->dump(start, size);
}

} // namespace demu::hal::axil
