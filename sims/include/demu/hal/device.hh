#pragma once

#include "./allocator.hh"
#include "./hardware.hh"
#include "demu/generated/sys_def.hh"
#include <utility>

namespace demu::hal {
using namespace isa;

class Device : public Hardware {
public:
  explicit Device(sys_def::DeviceDescriptor desc) : desc_(std::move(desc)) {}

  ~Device() override = default;

  [[nodiscard]] auto base_address() const noexcept -> addr_t {
    return static_cast<addr_t>(desc_.base);
  }
  [[nodiscard]] auto address_range() const noexcept -> size_t {
    return static_cast<size_t>(desc_.size);
  }
  [[nodiscard]] auto name() const noexcept -> const char * override {
    return desc_.name.data();
  }
  [[nodiscard]] auto device_type() const noexcept -> sys_def::DeviceType {
    return desc_.type;
  }
  [[nodiscard]] auto descriptor() const noexcept
      -> const sys_def::DeviceDescriptor & {
    return desc_;
  }

  [[nodiscard]] auto owns_address(addr_t addr) const noexcept -> bool {
    const addr_t base = base_address();
    return addr >= base && addr < (base + address_range());
  }
  [[nodiscard]] auto is_valid_offset(addr_t offset) const noexcept -> bool {
    return offset < address_range();
  }
  [[nodiscard]] auto to_offset(addr_t addr) const noexcept -> addr_t {
    return addr - base_address();
  }

  // NOTE: Every derived device should have a memory allocator which takes up
  // some address spaces
  [[nodiscard]] virtual auto allocator() const noexcept -> MemoryAllocator * {
    return nullptr;
  }
  virtual void dump(addr_t start, size_t size) const noexcept {}

  auto load_binary(const std::string &filename, addr_t offset = 0) -> bool {
    auto *alloc = allocator();
    if (!alloc) {
      return false;
    }
    return alloc->load_binary(filename, offset);
  }

private:
  sys_def::DeviceDescriptor desc_;
};

} // namespace demu::hal
