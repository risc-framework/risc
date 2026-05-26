#pragma once

#include "../../allocator.hh"
#include "../../peripheral/sram/sram.hh"
#include "./slave.hh"

namespace demu::hal::axil {

class AXILiteSRAM final : public AXILiteSlave {
public:
  explicit AXILiteSRAM(const sys_def::DeviceDescriptor &desc)
      : AXILiteSlave(desc), sram_(std::make_unique<sram::SRAM>(desc)) {}

  ~AXILiteSRAM() override = default;

  void reset() override;
  void clock_tick() override;
  void dump(addr_t start, size_t size) const noexcept override;

  // Bypass
  [[nodiscard]] auto allocator() const noexcept -> MemoryAllocator * override {
    return sram_->allocator();
  }

private:
  std::unique_ptr<sram::SRAM> sram_;

  void process_writes();
  void process_reads();
};

} // namespace demu::hal::axil
