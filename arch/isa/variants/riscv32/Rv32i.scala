package arch.isa.variants.riscv32

import arch.isa.instructions._

object Rv32i extends Riscv32Isa with Riscv32Base {
  override def name: String =
    "rv32i"

  override def features: Seq[String] =
    Seq("i", "zicsr", "machine")

  override def nop: Option[InstructionForm] =
    Some(
      rv(
        id = "nop",
        pattern = "b0000000_00000_00000_000_00000_0010011",
        format = "i",
        asm = "nop",
        fields = iFields,
        operands = Seq.empty,
        semantic = sem("addi", InstructionCategory.Alu),
        attributes = Seq("alias"),
        aliasOf = Some("addi"),
        priority = 100,
        description = "alias for addi x0, x0, 0"
      )
    )

  override def forms: Seq[InstructionForm] =
    Seq(
      rv(
        "add",
        "b0000000_?????_?????_000_?????_0110011",
        "r",
        "add rd, rs1, rs2",
        rFields,
        Seq(rdW, rs1R, rs2R),
        sem("add", InstructionCategory.Alu)
      ),
      rv(
        "sub",
        "b0100000_?????_?????_000_?????_0110011",
        "r",
        "sub rd, rs1, rs2",
        rFields,
        Seq(rdW, rs1R, rs2R),
        sem("sub", InstructionCategory.Alu)
      ),
      rv(
        "sll",
        "b0000000_?????_?????_001_?????_0110011",
        "r",
        "sll rd, rs1, rs2",
        rFields,
        Seq(rdW, rs1R, rs2R),
        sem("sll", InstructionCategory.Alu)
      ),
      rv(
        "slt",
        "b0000000_?????_?????_010_?????_0110011",
        "r",
        "slt rd, rs1, rs2",
        rFields,
        Seq(rdW, rs1R, rs2R),
        sem("slt", InstructionCategory.Alu)
      ),
      rv(
        "sltu",
        "b0000000_?????_?????_011_?????_0110011",
        "r",
        "sltu rd, rs1, rs2",
        rFields,
        Seq(rdW, rs1R, rs2R),
        sem("sltu", InstructionCategory.Alu)
      ),
      rv(
        "xor",
        "b0000000_?????_?????_100_?????_0110011",
        "r",
        "xor rd, rs1, rs2",
        rFields,
        Seq(rdW, rs1R, rs2R),
        sem("xor", InstructionCategory.Alu)
      ),
      rv(
        "srl",
        "b0000000_?????_?????_101_?????_0110011",
        "r",
        "srl rd, rs1, rs2",
        rFields,
        Seq(rdW, rs1R, rs2R),
        sem("srl", InstructionCategory.Alu)
      ),
      rv(
        "sra",
        "b0100000_?????_?????_101_?????_0110011",
        "r",
        "sra rd, rs1, rs2",
        rFields,
        Seq(rdW, rs1R, rs2R),
        sem("sra", InstructionCategory.Alu)
      ),
      rv(
        "or",
        "b0000000_?????_?????_110_?????_0110011",
        "r",
        "or rd, rs1, rs2",
        rFields,
        Seq(rdW, rs1R, rs2R),
        sem("or", InstructionCategory.Alu)
      ),
      rv(
        "and",
        "b0000000_?????_?????_111_?????_0110011",
        "r",
        "and rd, rs1, rs2",
        rFields,
        Seq(rdW, rs1R, rs2R),
        sem("and", InstructionCategory.Alu)
      ),
      rv(
        "addi",
        "b???????_?????_?????_000_?????_0010011",
        "i",
        "addi rd, rs1, imm",
        iFields,
        Seq(rdW, rs1R, immI),
        sem("addi", InstructionCategory.Alu)
      ),
      rv(
        "slli",
        "b0000000_?????_?????_001_?????_0010011",
        "i",
        "slli rd, rs1, shamt",
        iFields,
        Seq(rdW, rs1R, shamt),
        sem("slli", InstructionCategory.Alu)
      ),
      rv(
        "slti",
        "b???????_?????_?????_010_?????_0010011",
        "i",
        "slti rd, rs1, imm",
        iFields,
        Seq(rdW, rs1R, immI),
        sem("slti", InstructionCategory.Alu)
      ),
      rv(
        "sltiu",
        "b???????_?????_?????_011_?????_0010011",
        "i",
        "sltiu rd, rs1, imm",
        iFields,
        Seq(rdW, rs1R, immI),
        sem("sltiu", InstructionCategory.Alu)
      ),
      rv(
        "xori",
        "b???????_?????_?????_100_?????_0010011",
        "i",
        "xori rd, rs1, imm",
        iFields,
        Seq(rdW, rs1R, immI),
        sem("xori", InstructionCategory.Alu)
      ),
      rv(
        "srli",
        "b0000000_?????_?????_101_?????_0010011",
        "i",
        "srli rd, rs1, shamt",
        iFields,
        Seq(rdW, rs1R, shamt),
        sem("srli", InstructionCategory.Alu)
      ),
      rv(
        "srai",
        "b0100000_?????_?????_101_?????_0010011",
        "i",
        "srai rd, rs1, shamt",
        iFields,
        Seq(rdW, rs1R, shamt),
        sem("srai", InstructionCategory.Alu)
      ),
      rv(
        "ori",
        "b???????_?????_?????_110_?????_0010011",
        "i",
        "ori rd, rs1, imm",
        iFields,
        Seq(rdW, rs1R, immI),
        sem("ori", InstructionCategory.Alu)
      ),
      rv(
        "andi",
        "b???????_?????_?????_111_?????_0010011",
        "i",
        "andi rd, rs1, imm",
        iFields,
        Seq(rdW, rs1R, immI),
        sem("andi", InstructionCategory.Alu)
      ),
      rv(
        "lb",
        "b???????_?????_?????_000_?????_0000011",
        "i",
        "lb rd, imm(rs1)",
        iFields,
        Seq(rdW, rs1R, immI),
        sem("lb", InstructionCategory.Load, memory = MemoryAccessKind.Read, mayTrap = true),
        Seq("size=1", "signed")
      ),
      rv(
        "lh",
        "b???????_?????_?????_001_?????_0000011",
        "i",
        "lh rd, imm(rs1)",
        iFields,
        Seq(rdW, rs1R, immI),
        sem("lh", InstructionCategory.Load, memory = MemoryAccessKind.Read, mayTrap = true),
        Seq("size=2", "signed")
      ),
      rv(
        "lw",
        "b???????_?????_?????_010_?????_0000011",
        "i",
        "lw rd, imm(rs1)",
        iFields,
        Seq(rdW, rs1R, immI),
        sem("lw", InstructionCategory.Load, memory = MemoryAccessKind.Read, mayTrap = true),
        Seq("size=4", "signed")
      ),
      rv(
        "lbu",
        "b???????_?????_?????_100_?????_0000011",
        "i",
        "lbu rd, imm(rs1)",
        iFields,
        Seq(rdW, rs1R, immI),
        sem("lbu", InstructionCategory.Load, memory = MemoryAccessKind.Read, mayTrap = true),
        Seq("size=1", "unsigned")
      ),
      rv(
        "lhu",
        "b???????_?????_?????_101_?????_0000011",
        "i",
        "lhu rd, imm(rs1)",
        iFields,
        Seq(rdW, rs1R, immI),
        sem("lhu", InstructionCategory.Load, memory = MemoryAccessKind.Read, mayTrap = true),
        Seq("size=2", "unsigned")
      ),
      rv(
        "jalr",
        "b???????_?????_?????_000_?????_1100111",
        "i",
        "jalr rd, imm(rs1)",
        iFields,
        Seq(rdW, rs1R, immI),
        sem(
          "jalr",
          InstructionCategory.Jump,
          controlFlow = ControlFlowKind.Jump,
          readsPc = true,
          writesPc = true,
          mayTrap = true
        )
      ),
      rv(
        "csrrw",
        "b???????_?????_?????_001_?????_1110011",
        "csr",
        "csrrw rd, csr, rs1",
        csrFields,
        Seq(rdW, csrRW, rs1R),
        sem("csrrw", InstructionCategory.Csr, mayTrap = true, serializing = true)
      ),
      rv(
        "csrrs",
        "b???????_?????_?????_010_?????_1110011",
        "csr",
        "csrrs rd, csr, rs1",
        csrFields,
        Seq(rdW, csrRW, rs1R),
        sem("csrrs", InstructionCategory.Csr, mayTrap = true, serializing = true)
      ),
      rv(
        "csrrc",
        "b???????_?????_?????_011_?????_1110011",
        "csr",
        "csrrc rd, csr, rs1",
        csrFields,
        Seq(rdW, csrRW, rs1R),
        sem("csrrc", InstructionCategory.Csr, mayTrap = true, serializing = true)
      ),
      rv(
        "csrrwi",
        "b???????_?????_?????_101_?????_1110011",
        "csr",
        "csrrwi rd, csr, zimm",
        csrFields,
        Seq(rdW, csrRW, zimm),
        sem("csrrwi", InstructionCategory.Csr, mayTrap = true, serializing = true)
      ),
      rv(
        "csrrsi",
        "b???????_?????_?????_110_?????_1110011",
        "csr",
        "csrrsi rd, csr, zimm",
        csrFields,
        Seq(rdW, csrRW, zimm),
        sem("csrrsi", InstructionCategory.Csr, mayTrap = true, serializing = true)
      ),
      rv(
        "csrrci",
        "b???????_?????_?????_111_?????_1110011",
        "csr",
        "csrrci rd, csr, zimm",
        csrFields,
        Seq(rdW, csrRW, zimm),
        sem("csrrci", InstructionCategory.Csr, mayTrap = true, serializing = true)
      ),
      rv(
        "sb",
        "b???????_?????_?????_000_?????_0100011",
        "s",
        "sb rs2, imm(rs1)",
        sFields,
        Seq(rs2R, rs1R, immS),
        sem("sb", InstructionCategory.Store, memory = MemoryAccessKind.Write, mayTrap = true),
        Seq("size=1")
      ),
      rv(
        "sh",
        "b???????_?????_?????_001_?????_0100011",
        "s",
        "sh rs2, imm(rs1)",
        sFields,
        Seq(rs2R, rs1R, immS),
        sem("sh", InstructionCategory.Store, memory = MemoryAccessKind.Write, mayTrap = true),
        Seq("size=2")
      ),
      rv(
        "sw",
        "b???????_?????_?????_010_?????_0100011",
        "s",
        "sw rs2, imm(rs1)",
        sFields,
        Seq(rs2R, rs1R, immS),
        sem("sw", InstructionCategory.Store, memory = MemoryAccessKind.Write, mayTrap = true),
        Seq("size=4")
      ),
      rv(
        "beq",
        "b???????_?????_?????_000_?????_1100011",
        "b",
        "beq rs1, rs2, imm",
        bFields,
        Seq(rs1R, rs2R, immB),
        sem(
          "beq",
          InstructionCategory.Branch,
          controlFlow = ControlFlowKind.Branch,
          readsPc = true,
          writesPc = true
        )
      ),
      rv(
        "bne",
        "b???????_?????_?????_001_?????_1100011",
        "b",
        "bne rs1, rs2, imm",
        bFields,
        Seq(rs1R, rs2R, immB),
        sem(
          "bne",
          InstructionCategory.Branch,
          controlFlow = ControlFlowKind.Branch,
          readsPc = true,
          writesPc = true
        )
      ),
      rv(
        "blt",
        "b???????_?????_?????_100_?????_1100011",
        "b",
        "blt rs1, rs2, imm",
        bFields,
        Seq(rs1R, rs2R, immB),
        sem(
          "blt",
          InstructionCategory.Branch,
          controlFlow = ControlFlowKind.Branch,
          readsPc = true,
          writesPc = true
        )
      ),
      rv(
        "bge",
        "b???????_?????_?????_101_?????_1100011",
        "b",
        "bge rs1, rs2, imm",
        bFields,
        Seq(rs1R, rs2R, immB),
        sem(
          "bge",
          InstructionCategory.Branch,
          controlFlow = ControlFlowKind.Branch,
          readsPc = true,
          writesPc = true
        )
      ),
      rv(
        "bltu",
        "b???????_?????_?????_110_?????_1100011",
        "b",
        "bltu rs1, rs2, imm",
        bFields,
        Seq(rs1R, rs2R, immB),
        sem(
          "bltu",
          InstructionCategory.Branch,
          controlFlow = ControlFlowKind.Branch,
          readsPc = true,
          writesPc = true
        )
      ),
      rv(
        "bgeu",
        "b???????_?????_?????_111_?????_1100011",
        "b",
        "bgeu rs1, rs2, imm",
        bFields,
        Seq(rs1R, rs2R, immB),
        sem(
          "bgeu",
          InstructionCategory.Branch,
          controlFlow = ControlFlowKind.Branch,
          readsPc = true,
          writesPc = true
        )
      ),
      rv(
        "lui",
        "b???????_?????_?????_???_?????_0110111",
        "u",
        "lui rd, imm",
        uFields,
        Seq(rdW, immU),
        sem("lui", InstructionCategory.Alu)
      ),
      rv(
        "auipc",
        "b???????_?????_?????_???_?????_0010111",
        "u",
        "auipc rd, imm",
        uFields,
        Seq(rdW, immU),
        sem("auipc", InstructionCategory.Alu, readsPc = true)
      ),
      rv(
        "jal",
        "b???????_?????_?????_???_?????_1101111",
        "j",
        "jal rd, imm",
        jFields,
        Seq(rdW, immJ),
        sem(
          "jal",
          InstructionCategory.Jump,
          controlFlow = ControlFlowKind.Jump,
          readsPc = true,
          writesPc = true
        )
      ),
      rv(
        "ecall",
        "b00000000000000000000000001110011",
        "system",
        "ecall",
        systemFields,
        Seq.empty,
        sem(
          "ecall",
          InstructionCategory.System,
          controlFlow = ControlFlowKind.Trap,
          mayTrap = true,
          serializing = true
        )
      ),
      rv(
        "ebreak",
        "b00000000000100000000000001110011",
        "system",
        "ebreak",
        systemFields,
        Seq.empty,
        sem(
          "ebreak",
          InstructionCategory.System,
          controlFlow = ControlFlowKind.Trap,
          mayTrap = true,
          serializing = true
        )
      ),
      rv(
        "mret",
        "b00110000001000000000000001110011",
        "system",
        "mret",
        systemFields,
        Seq.empty,
        sem(
          "mret",
          InstructionCategory.System,
          privilege = PrivilegeLevel.Machine,
          controlFlow = ControlFlowKind.Eret,
          writesPc = true,
          mayTrap = true,
          serializing = true
        )
      )
    )
}
