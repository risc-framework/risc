#pragma once

#include "demu/generated/isa_def.hh"
#include <cstdint>
#include <string>

namespace demu::isa {
using demu::isa_def::instr_t;

enum InstrType {
  R_TYPE,
  I_TYPE,
  S_TYPE,
  B_TYPE,
  U_TYPE,
  J_TYPE,
  SYSTEM,
  UNKNOWN
};

enum SystemInstr {
  EBREAK = 0x00100073,
  SAFE_LOOP = 0x0000006f,
  BUBBLE = 0x00000013,
  URET = 0x00200073,
  SRET = 0x20200073,
  MRET = 0x30200073,
};

enum CsrAddrMap {
  // U-Mode
  CYCLE = 0xC00,
  INSTRET = 0xC02,

  // M-Mode
  MSTATUS = 0x300,
  MISA = 0x301,
  MIE = 0x304,
  MTVEC = 0x305,
  MSCRATCH = 0x340,
  MEPC = 0x341,
  MCAUSE = 0x342,
  MIP = 0x344,
  MCYCLE = 0xB00,
  MVENDERID = 0xF11,
  MARCHID = 0xF12,
  MIMPID = 0xF13,
  MHARTID = 0xF14,
};

class Instruction {
public:
  explicit Instruction(instr_t raw);

  [[nodiscard]] auto type() const noexcept -> InstrType;
  [[nodiscard]] auto mnemonic() const noexcept -> std::string;
  [[nodiscard]] auto to_string() const -> std::string;
  [[nodiscard]] auto csr_name() const -> std::string;

  [[nodiscard]] auto opcode() const noexcept -> uint8_t { return opcode_; }
  [[nodiscard]] auto rd() const noexcept -> uint8_t { return rd_; }
  [[nodiscard]] auto rs1() const noexcept -> uint8_t { return rs1_; }
  [[nodiscard]] auto rs2() const noexcept -> uint8_t { return rs2_; }
  [[nodiscard]] auto funct3() const noexcept -> uint8_t { return funct3_; }
  [[nodiscard]] auto funct7() const noexcept -> uint8_t { return funct7_; }
  [[nodiscard]] auto imm() const noexcept -> int32_t { return imm_; }

private:
  instr_t raw_;
  uint8_t opcode_;
  uint8_t rd_;
  uint8_t rs1_;
  uint8_t rs2_;
  uint8_t funct3_;
  uint8_t funct7_;
  int32_t imm_;

  void decode();
  [[nodiscard]] auto decode_i_imm() const noexcept -> int32_t;
  [[nodiscard]] auto decode_s_imm() const noexcept -> int32_t;
  [[nodiscard]] auto decode_b_imm() const noexcept -> int32_t;
  [[nodiscard]] auto decode_u_imm() const noexcept -> int32_t;
  [[nodiscard]] auto decode_j_imm() const noexcept -> int32_t;
};

} // namespace demu::isa
