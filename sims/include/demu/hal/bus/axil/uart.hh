#pragma once

#include "../../allocator.hh"
#include "../../peripheral/uart/uart.hh"
#include "./slave.hh"

namespace demu::hal::axil {

class AXILiteUART final : public AXILiteSlave {
public:
  explicit AXILiteUART(const sys_def::DeviceDescriptor &desc)
      : AXILiteSlave(desc), uart_(std::make_unique<uart::UART>(desc)) {}

  ~AXILiteUART() override = default;

  void reset() override;
  void clock_tick() override;
  void dump(addr_t start, size_t size) const noexcept override;

  // Bypass
  [[nodiscard]] auto allocator() const noexcept -> MemoryAllocator * override {
    return uart_->allocator();
  }

private:
  std::unique_ptr<uart::UART> uart_;

  void process_writes();
  void process_reads();
};

} // namespace demu::hal::axil
