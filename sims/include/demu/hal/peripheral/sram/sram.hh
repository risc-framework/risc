#pragma once

#include "../../allocator.hh"
#include "../../device.hh"

namespace demu::hal::sram {
using namespace isa;

class SRAM final : public Device {
public:
  explicit SRAM(const sys_def::DeviceDescriptor &desc)
      : Device(desc),
        memory_(std::make_unique<MemoryAllocator>(desc.base, desc.size)) {}

  ~SRAM() override = default;

  [[nodiscard]] auto allocator() const noexcept -> MemoryAllocator * override {
    return memory_.get();
  }

  void clock_tick() override;
  void reset() override;
  void dump(addr_t start, size_t size) const noexcept override;

private:
  std::unique_ptr<MemoryAllocator> memory_;
};

} // namespace demu::hal::sram
