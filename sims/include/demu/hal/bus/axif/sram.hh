#pragma once
#include "../../allocator.hh"
#include "../../peripheral/sram/sram.hh"
#include "./slave.hh"

namespace demu::hal::axif {

class AXIFullSRAM final : public AXIFullSlave {
public:
  explicit AXIFullSRAM(const sys_def::DeviceDescriptor &desc)
      : AXIFullSlave(desc), sram_(std::make_unique<sram::SRAM>(desc)) {}
  ~AXIFullSRAM() override = default;

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
  void calculate_next_address(BurstTransaction &req);
};

} // namespace demu::hal::axif
