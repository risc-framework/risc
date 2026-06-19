package arch.isa.variants.riscv32

import arch.isa._

trait Riscv32Base { this: Riscv32IsaDefinition =>
  final protected val opcode: InstructionField = InstructionField("opcode", 6, 0, role = "opcode")
  final protected val rd: InstructionField     = InstructionField("rd", 11, 7, role = "rd")
  final protected val funct3: InstructionField = InstructionField("funct3", 14, 12, role = "funct3")
  final protected val rs1: InstructionField    = InstructionField("rs1", 19, 15, role = "rs1")
  final protected val rs2: InstructionField    = InstructionField("rs2", 24, 20, role = "rs2")
  final protected val funct7: InstructionField = InstructionField("funct7", 31, 25, role = "funct7")
  final protected val csr: InstructionField    = InstructionField("csr", 31, 20, role = "csr")

  final protected val rFields: Seq[InstructionField]      = Seq(funct7, rs2, rs1, funct3, rd, opcode)
  final protected val iFields: Seq[InstructionField]      =
    Seq(InstructionField("imm", 31, 20, signed = true, role = "imm"), rs1, funct3, rd, opcode)
  final protected val sFields: Seq[InstructionField]      = Seq(
    InstructionField("imm_hi", 31, 25, signed = true, role = "imm"),
    rs2,
    rs1,
    funct3,
    InstructionField("imm_lo", 11, 7, role = "imm"),
    opcode
  )
  final protected val bFields: Seq[InstructionField]      = Seq(
    InstructionField("imm_12_10_5", 31, 25, signed = true, role = "imm"),
    rs2,
    rs1,
    funct3,
    InstructionField("imm_4_1_11", 11, 7, role = "imm"),
    opcode
  )
  final protected val uFields: Seq[InstructionField]      =
    Seq(InstructionField("imm", 31, 12, signed = true, role = "imm"), rd, opcode)
  final protected val jFields: Seq[InstructionField]      =
    Seq(InstructionField("imm", 31, 12, signed = true, role = "imm"), rd, opcode)
  final protected val csrFields: Seq[InstructionField]    = Seq(csr, rs1, funct3, rd, opcode)
  final protected val systemFields: Seq[InstructionField] = Seq(opcode)

  final val rdW: InstructionOperand   = InstructionOperand(
    "rd",
    OperandKind.Register,
    OperandAccess.Write,
    width = 5,
    pieces = Seq(OperandPiece(11, 7, 0))
  )
  final val rs1R: InstructionOperand  = InstructionOperand(
    "rs1",
    OperandKind.Register,
    OperandAccess.Read,
    width = 5,
    pieces = Seq(OperandPiece(19, 15, 0))
  )
  final val rs2R: InstructionOperand  = InstructionOperand(
    "rs2",
    OperandKind.Register,
    OperandAccess.Read,
    width = 5,
    pieces = Seq(OperandPiece(24, 20, 0))
  )
  final val csrRW: InstructionOperand = InstructionOperand(
    "csr",
    OperandKind.Csr,
    OperandAccess.ReadWrite,
    width = 12,
    pieces = Seq(OperandPiece(31, 20, 0))
  )
  final val zimm: InstructionOperand  = InstructionOperand(
    "zimm",
    OperandKind.Immediate,
    OperandAccess.Read,
    width = 5,
    signed = false,
    pieces = Seq(OperandPiece(19, 15, 0))
  )
  final val shamt: InstructionOperand = InstructionOperand(
    "shamt",
    OperandKind.Immediate,
    OperandAccess.Read,
    width = 5,
    signed = false,
    pieces = Seq(OperandPiece(24, 20, 0))
  )
  final val immI: InstructionOperand  = InstructionOperand(
    "imm",
    OperandKind.Immediate,
    OperandAccess.Read,
    width = 12,
    signed = true,
    pieces = Seq(OperandPiece(31, 20, 0))
  )
  final val immS: InstructionOperand  = InstructionOperand(
    "imm",
    OperandKind.Immediate,
    OperandAccess.Read,
    width = 12,
    signed = true,
    pieces = Seq(OperandPiece(31, 25, 5), OperandPiece(11, 7, 0))
  )
  final val immB: InstructionOperand  = InstructionOperand(
    "imm",
    OperandKind.Immediate,
    OperandAccess.Read,
    width = 13,
    signed = true,
    pieces = Seq(
      OperandPiece(31, 31, 12),
      OperandPiece(7, 7, 11),
      OperandPiece(30, 25, 5),
      OperandPiece(11, 8, 1)
    )
  )
  final val immU: InstructionOperand  = InstructionOperand(
    "imm",
    OperandKind.Immediate,
    OperandAccess.Read,
    width = 32,
    signed = true,
    pieces = Seq(OperandPiece(31, 12, 12))
  )
  final val immJ: InstructionOperand  = InstructionOperand(
    "imm",
    OperandKind.Immediate,
    OperandAccess.Read,
    width = 21,
    signed = true,
    pieces = Seq(
      OperandPiece(31, 31, 20),
      OperandPiece(19, 12, 12),
      OperandPiece(20, 20, 11),
      OperandPiece(30, 21, 1)
    )
  )

  final protected def sem(
    id: String,
    category: String,
    extension: String = "i",
    privilege: String = PrivilegeLevel.Any,
    controlFlow: String = ControlFlowKind.None,
    memory: String = MemoryAccessKind.None,
    readsPc: Boolean = false,
    writesPc: Boolean = false,
    mayTrap: Boolean = false,
    serializing: Boolean = false,
    notes: String = ""
  ): InstructionSemantic =
    InstructionSemantic(
      id = id,
      category = category,
      extension = extension,
      privilege = privilege,
      controlFlow = controlFlow,
      memory = memory,
      readsPc = readsPc,
      writesPc = writesPc,
      mayTrap = mayTrap,
      serializing = serializing,
      notes = notes
    )

  final protected def rv(
    id: String,
    pattern: String,
    format: String,
    asm: String,
    fields: Seq[InstructionField],
    operands: Seq[InstructionOperand],
    semantic: InstructionSemantic,
    attributes: Seq[String] = Seq.empty,
    aliasOf: Option[String] = None,
    priority: Int = 0,
    description: String = ""
  ): InstructionForm =
    fixed(
      id = id,
      pattern = pattern,
      format = format,
      asm = asm,
      fields = fields,
      operands = operands,
      semantic = semantic,
      attributes = attributes,
      aliasOf = aliasOf,
      priority = priority,
      description = description
    )
}
