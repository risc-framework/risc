#pragma once

#include "demu/hal/bus/axif/slave.hh"
#include "demu/hal/peripheral/uart/uart.hh"

namespace demu::hal::axif {

class AXIFullUART final : public AXIFullSlave {
public:
  explicit AXIFullUART(const sys_def::DeviceDescriptor &desc)
      : AXIFullSlave(desc), uart_(std::make_unique<uart::UART>(desc)) {}

  ~AXIFullUART() override = default;

  void reset() override;
  void clock_tick() override;
  void dump(addr_t start, size_t size) const noexcept override;

  // Bypass MemoryAllocator to the underlying peripheral
  [[nodiscard]] auto allocator() const noexcept -> MemoryAllocator * override {
    return uart_->allocator();
  }

private:
  std::unique_ptr<uart::UART> uart_;

  void process_writes();
  void process_reads();
  void calculate_next_address(BurstTransaction &req);
};

} // namespace demu::hal::axif
