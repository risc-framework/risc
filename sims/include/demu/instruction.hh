#pragma once

#include "demu/generated/isa_def.hh"
#include <cstdint>
#include <iomanip>
#include <sstream>
#include <string>
#include <string_view>

namespace demu {
using demu::isa_def::instr_t;

class Instruction {
public:
  explicit constexpr Instruction(instr_t raw) noexcept : raw_(raw) {}

  [[nodiscard]] constexpr auto raw() const noexcept -> instr_t { return raw_; }

  [[nodiscard]] constexpr auto encoding() const noexcept
      -> const demu::isa_def::InstructionEncoding * {
    for (const auto &enc : demu::isa_def::INSTRUCTION_ENCODINGS) {
      const auto value = static_cast<instr_t>(enc.value);
      const auto mask = static_cast<instr_t>(enc.mask);

      if ((raw_ & mask) == value) {
        return &enc;
      }
    }

    return nullptr;
  }

  [[nodiscard]] constexpr auto known() const noexcept -> bool {
    return encoding() != nullptr;
  }

  [[nodiscard]] constexpr auto unknown() const noexcept -> bool {
    return !known();
  }

  [[nodiscard]] constexpr auto nop() const noexcept -> bool {
    const auto value = static_cast<instr_t>(demu::isa_def::ISA_NOP.value);
    const auto mask = static_cast<instr_t>(demu::isa_def::ISA_NOP.mask);

    return (raw_ & mask) == value;
  }

  [[nodiscard]] auto name() const noexcept -> std::string_view {
    if (const auto *enc = encoding()) {
      return enc->name;
    }

    return "UNKNOWN";
  }

  [[nodiscard]] auto mnemonic_view() const noexcept -> std::string_view {
    return name();
  }

  [[nodiscard]] auto mnemonic() const -> std::string {
    return std::string(name());
  }

  [[nodiscard]] auto to_string() const -> std::string {
    std::ostringstream oss;

    oss << std::left << std::setw(10) << std::string(name());

    oss << "0x" << std::right << std::hex << std::setw(demu::isa_def::ILEN / 4)
        << std::setfill('0') << static_cast<uint64_t>(raw_);

    return oss.str();
  }

private:
  instr_t raw_{0};
};

} // namespace demu
