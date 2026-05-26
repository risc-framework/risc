#pragma once

#include "demu/generated/isa_def.hh"
#include "demu/logger.hh"
#include <string>
#include <vector>

namespace demu::hal {
using isa_def::addr_t;
using isa_def::byte_t;
using isa_def::half_t;
using isa_def::word_t;

class MemoryAllocator final {
public:
  MemoryAllocator(addr_t base_addr, size_t size);
  ~MemoryAllocator() = default;

  template <typename T>
  [[nodiscard]] auto read(addr_t addr) const noexcept -> T {
    if (!is_valid_addr(addr)) {
      HAL_WARN("Invalid Read at 0x{:08x}", addr);
      return T{};
    }
    T val;
    std::memcpy(&val, memory_.data() + to_offset(addr), sizeof(T));
    return val;
  }
  [[nodiscard]] inline auto read_word(addr_t addr) const noexcept -> word_t {
    return read<word_t>(addr);
  }
  [[nodiscard]] inline auto read_half(addr_t addr) const noexcept -> half_t {
    return read<half_t>(addr);
  }
  [[nodiscard]] inline auto read_byte(addr_t addr) const noexcept -> byte_t {
    return read<byte_t>(addr);
  }

  template <typename T> void write(addr_t addr, T data) {
    if (!is_valid_addr(addr)) {
      HAL_WARN("Invalid Write at 0x{:08x}", addr);
      return;
    }
    std::memcpy(memory_.data() + to_offset(addr), &data, sizeof(T));
  }
  inline void write_word(addr_t addr, word_t data) {
    write<word_t>(addr, data);
  }
  inline void write_half(addr_t addr, half_t data) {
    write<half_t>(addr, data);
  }
  inline void write_byte(addr_t addr, byte_t data) {
    write<byte_t>(addr, data);
  }

  // Helpers
  [[nodiscard]] auto is_valid_addr(addr_t addr) const noexcept -> bool;
  [[nodiscard]] auto to_offset(addr_t addr) const noexcept -> addr_t;
  auto load_binary(const std::string &filename, addr_t offset = 0) -> bool;
  void dump(addr_t start, addr_t length) const;
  void clear();

  // Direct access
  [[nodiscard]] auto data() noexcept -> byte_t * { return memory_.data(); }
  [[nodiscard]] auto size() const noexcept -> size_t { return memory_.size(); }
  [[nodiscard]] auto base_address() const noexcept -> addr_t {
    return base_addr_;
  }
  [[nodiscard]] auto get_ptr(addr_t addr) -> byte_t * {
    if (!is_valid_addr(addr)) {
      HAL_WARN("Invalid memory access at address 0x{:08x}", addr);
      return nullptr;
    }
    return &memory_[to_offset(addr)];
  }

private:
  // components
  std::vector<byte_t> memory_;
  addr_t base_addr_;
};

} // namespace demu::hal
