#pragma once

#include "demu/generated/isa_def.hh"
#include <cstdint>
#include <memory>
#include <string>

namespace demu::difftest {
using isa_def::addr_t;
using isa_def::byte_t;
using isa_def::word_t;

struct CPU_state {
  word_t gpr[isa_def::NUM_ARCH_REGS];
  addr_t pc;
};

class IRefModel {
public:
  virtual ~IRefModel() = default;

  virtual auto init() -> bool = 0;
  virtual void sync_memory(addr_t addr, size_t size, const void *data) = 0;
  virtual void step(uint64_t n) = 0;

  virtual void push_state() = 0;
  virtual void pull_state() = 0;

  [[nodiscard]] virtual auto get_pc() const -> addr_t = 0;
  [[nodiscard]] virtual auto get_reg(uint8_t idx) const -> word_t = 0;
  virtual void set_pc(addr_t pc) = 0;
  virtual void set_reg(uint8_t idx, word_t val) = 0;
};

auto create_ref_model(const std::string &so_path) -> std::unique_ptr<IRefModel>;

} // namespace demu::difftest
