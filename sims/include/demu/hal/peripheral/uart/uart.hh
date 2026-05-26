#pragma once

#include "../../allocator.hh"
#include "../../device.hh"
#include <memory>

namespace demu::hal::uart {
using namespace isa;

enum UartRegisters : addr_t {
  UART_RXD = 0x00,
  UART_TXD = 0x04,
  UART_TXC = 0x08, // TX Control/Status
  UART_RXC = 0x0C, // RX Control/Status
  UART_BAUDIV = 0x10,
};

class UART final : public Device {
public:
  explicit UART(const sys_def::DeviceDescriptor &desc)
      : Device(desc), allocator_(std::make_unique<MemoryAllocator>(
                          base_address(), address_range())) {}

  ~UART() override = default;

  [[nodiscard]] auto allocator() const noexcept -> MemoryAllocator * override {
    return allocator_.get();
  }

  void clock_tick() override;
  void reset() override;
  void dump(addr_t start, size_t size) const noexcept override;

private:
  std::unique_ptr<MemoryAllocator> allocator_;
};

} // namespace demu::hal::uart
