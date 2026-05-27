#pragma once

#include "demu/hal/allocator.hh"
#include "demu/hal/bus/axif/slave.hh"
#include "demu/hal/interrupt.hh"

#if defined(__ISA_RV32I__) || defined(__ISA_RV32IM__)

namespace demu::hal::axif {

enum ClintRegisters : addr_t {
  CLINT_MSIP = 0x0000,
  CLINT_MTIMECMP_LO = 0x4000,
  CLINT_MTIMECMP_HI = 0x4004,
  CLINT_MTIME_LO = 0xBFF8,
  CLINT_MTIME_HI = 0xBFFC
};

constexpr const uint64_t TICK_MS_DIVIDER = 1000;
constexpr const uint64_t TICK_US_DIVIDER = TICK_MS_DIVIDER * 1000;
constexpr const uint64_t TICK_NS_DIVIDER = TICK_US_DIVIDER * 1000;

class AXIFullCLINT final : public AXIFullSlave {
public:
  explicit AXIFullCLINT(const sys_def::DeviceDescriptor &desc, uint64_t freq,
                        InterruptLine *timer_line = nullptr,
                        InterruptLine *soft_line = nullptr)
      : AXIFullSlave(desc),
        allocator_(std::make_unique<MemoryAllocator>(desc.base, desc.size)),
        freq_(freq), timer_line_(timer_line), soft_line_(soft_line) {}

  ~AXIFullCLINT() override = default;

  void reset() override;
  void clock_tick() override;
  void dump(addr_t start, size_t size) const noexcept override;

  // Bypass
  [[nodiscard]] auto allocator() const noexcept -> MemoryAllocator * override {
    return allocator_.get();
  }

private:
  std::unique_ptr<MemoryAllocator> allocator_;
  uint64_t freq_;
  InterruptLine *timer_line_;
  InterruptLine *soft_line_;

  void process_writes();
  void process_reads();
  void calculate_next_address(BurstTransaction &req);
};

} // namespace demu::hal::axif

#endif // defined(__ISA_RV32I__) || defined(__ISA_RV32IM__)
