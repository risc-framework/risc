#pragma once

#include "demu/generated/isa_def.hh"
#include <cstdint>
#include <iomanip>
#include <sstream>
#include <string>
#include <string_view>

namespace demu {

using demu::isa_def::instr_t;

enum class InstructionLogLevel {
  Compact,
  Decode,
  Verbose,
};

class Instruction {
public:
  explicit constexpr Instruction(instr_t raw) noexcept : raw_(raw) {}

  [[nodiscard]] constexpr auto raw() const noexcept -> instr_t { return raw_; }

  [[nodiscard]] static constexpr auto
  is_fixed_bits(const demu::isa_def::InstructionForm &form) noexcept -> bool {
    return form.encoding_kind == std::string_view{"fixed_bits"};
  }

  [[nodiscard]] static constexpr auto
  fixed_match(const demu::isa_def::InstructionForm &form, instr_t raw) noexcept
      -> bool {
    if (!is_fixed_bits(form)) {
      return false;
    }

    if (form.fixed_bits == 0 || form.fixed_bits > sizeof(instr_t) * 8u) {
      return false;
    }

    const auto value = static_cast<instr_t>(form.value);
    const auto mask = static_cast<instr_t>(form.mask);

    return (raw & mask) == value;
  }

  [[nodiscard]] constexpr auto form() const noexcept
      -> const demu::isa_def::InstructionForm * {
    if (fixed_match(demu::isa_def::ISA_NOP, raw_)) {
      return &demu::isa_def::ISA_NOP;
    }

    for (const auto &entry : demu::isa_def::INSTRUCTION_FORMS) {
      if (fixed_match(entry, raw_)) {
        return &entry;
      }
    }

    return nullptr;
  }

  [[nodiscard]] constexpr auto known() const noexcept -> bool {
    return form() != nullptr;
  }

  [[nodiscard]] constexpr auto unknown() const noexcept -> bool {
    return !known();
  }

  [[nodiscard]] constexpr auto nop() const noexcept -> bool {
    return fixed_match(demu::isa_def::ISA_NOP, raw_);
  }

  [[nodiscard]] auto name() const noexcept -> std::string_view {
    if (const auto *entry = form()) {
      return entry->name;
    }

    return "UNKNOWN";
  }

  [[nodiscard]] auto id() const noexcept -> std::string_view {
    if (const auto *entry = form()) {
      return entry->id;
    }

    return "unknown";
  }

  [[nodiscard]] auto semantic_id() const noexcept -> std::string_view {
    if (const auto *entry = form()) {
      return entry->semantic_id;
    }

    return "unknown";
  }

  [[nodiscard]] auto category() const noexcept -> std::string_view {
    if (const auto *entry = form()) {
      return entry->category;
    }

    return "unknown";
  }

  [[nodiscard]] auto format() const noexcept -> std::string_view {
    if (const auto *entry = form()) {
      return entry->format;
    }

    return "";
  }

  [[nodiscard]] auto asm_template() const noexcept -> std::string_view {
    if (const auto *entry = form()) {
      return entry->asm_template;
    }

    return "";
  }

  [[nodiscard]] auto control_flow() const noexcept -> std::string_view {
    if (const auto *entry = form()) {
      return entry->control_flow;
    }

    return "none";
  }

  [[nodiscard]] auto memory() const noexcept -> std::string_view {
    if (const auto *entry = form()) {
      return entry->memory;
    }

    return "none";
  }

  [[nodiscard]] auto mnemonic_view() const noexcept -> std::string_view {
    return name();
  }

  [[nodiscard]] auto mnemonic() const -> std::string {
    return std::string(name());
  }

  [[nodiscard]] auto raw_hex() const -> std::string {
    std::ostringstream oss;
    oss << "0x" << std::hex << std::setw(demu::isa_def::ILEN / 4)
        << std::setfill('0') << static_cast<uint64_t>(raw_);
    return oss.str();
  }

  [[nodiscard]] auto compact_string() const -> std::string {
    std::ostringstream oss;
    oss << std::left << std::setw(8) << std::string(name()) << raw_hex();
    return oss.str();
  }

  [[nodiscard]] auto decode_string() const -> std::string {
    std::ostringstream oss;
    oss << compact_string();

    if (known()) {
      oss << " [" << category();

      if (!format().empty()) {
        oss << "/" << format();
      }

      oss << "]";
    }

    return oss.str();
  }

  [[nodiscard]] auto verbose_string() const -> std::string {
    std::ostringstream oss;
    oss << compact_string();

    if (known()) {
      oss << " {id=" << id() << ", semantic=" << semantic_id()
          << ", category=" << category() << ", format=" << format()
          << ", control=" << control_flow() << ", memory=" << memory();

      if (const auto templ = asm_template(); !templ.empty()) {
        oss << ", asm=\"" << templ << "\"";
      }

      oss << "}";
    }

    return oss.str();
  }

  [[nodiscard]] auto
  to_string(InstructionLogLevel level = InstructionLogLevel::Compact) const
      -> std::string {
    switch (level) {
    case InstructionLogLevel::Compact:
      return compact_string();
    case InstructionLogLevel::Decode:
      return decode_string();
    case InstructionLogLevel::Verbose:
      return verbose_string();
    }

    return compact_string();
  }

private:
  instr_t raw_{0};
};

} // namespace demu
