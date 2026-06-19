package arch.isa.instructions.variants.a64

import arch.isa.instructions._

trait A64Base { this: A64IsaDefinition =>
  final protected val sf: InstructionField      = InstructionField("sf", 31, 31, role = "datasize")
  final protected val op: InstructionField      = InstructionField("op", 30, 30, role = "opcode")
  final protected val s: InstructionField       = InstructionField("s", 29, 29, role = "flags")
  final protected val opc: InstructionField     = InstructionField("opc", 30, 29, role = "opcode")
  final protected val fixed: InstructionField   = InstructionField("fixed", 28, 24, role = "opcode")
  final protected val shift: InstructionField   = InstructionField("shift", 23, 22, role = "shift")
  final protected val n: InstructionField       = InstructionField("n", 21, 21, role = "invert")
  final protected val rm: InstructionField      = InstructionField("rm", 20, 16, role = "rm")
  final protected val imm6: InstructionField    = InstructionField("imm6", 15, 10, role = "imm")
  final protected val rn: InstructionField      = InstructionField("rn", 9, 5, role = "rn")
  final protected val rd: InstructionField      = InstructionField("rd", 4, 0, role = "rd")
  final protected val sh: InstructionField      = InstructionField("sh", 22, 22, role = "shift")
  final protected val imm12: InstructionField   = InstructionField("imm12", 21, 10, role = "imm")
  final protected val hw: InstructionField      = InstructionField("hw", 22, 21, role = "shift")
  final protected val imm16: InstructionField   = InstructionField("imm16", 20, 5, role = "imm")
  final protected val hintCrm: InstructionField = InstructionField("crm", 11, 8, role = "hint")
  final protected val hintOp2: InstructionField = InstructionField("op2", 7, 5, role = "hint")

  final protected val addSubShiftFields: Seq[InstructionField] =
    Seq(
      sf,
      op,
      s,
      fixed,
      shift,
      InstructionField("fixed21", 21, 21, role = "opcode"),
      rm,
      imm6,
      rn,
      rd
    )

  final protected val addSubImmFields: Seq[InstructionField] =
    Seq(sf, op, s, fixed, InstructionField("fixed23", 23, 23, role = "opcode"), sh, imm12, rn, rd)

  final protected val logicalShiftFields: Seq[InstructionField] =
    Seq(sf, opc, fixed, shift, n, rm, imm6, rn, rd)

  final protected val moveWideFields: Seq[InstructionField] =
    Seq(sf, opc, InstructionField("fixed28_23", 28, 23, role = "opcode"), hw, imm16, rd)

  final protected val hintFields: Seq[InstructionField] =
    Seq(InstructionField("fixed31_12", 31, 12, role = "opcode"), hintCrm, hintOp2, rd)

  final val rdW: InstructionOperand = InstructionOperand(
    "rd",
    OperandKind.Register,
    OperandAccess.Write,
    width = 5,
    pieces = Seq(OperandPiece(4, 0, 0))
  )

  final val rnR: InstructionOperand = InstructionOperand(
    "rn",
    OperandKind.Register,
    OperandAccess.Read,
    width = 5,
    pieces = Seq(OperandPiece(9, 5, 0))
  )

  final val rnRW: InstructionOperand = InstructionOperand(
    "rn",
    OperandKind.Register,
    OperandAccess.ReadWrite,
    width = 5,
    pieces = Seq(OperandPiece(9, 5, 0))
  )

  final val rmR: InstructionOperand = InstructionOperand(
    "rm",
    OperandKind.Register,
    OperandAccess.Read,
    width = 5,
    pieces = Seq(OperandPiece(20, 16, 0))
  )

  final val shiftR: InstructionOperand = InstructionOperand(
    "shift",
    OperandKind.Immediate,
    OperandAccess.Read,
    width = 2,
    signed = false,
    pieces = Seq(OperandPiece(23, 22, 0))
  )

  final val shamtR: InstructionOperand = InstructionOperand(
    "shamt",
    OperandKind.Immediate,
    OperandAccess.Read,
    width = 6,
    signed = false,
    pieces = Seq(OperandPiece(15, 10, 0))
  )

  final val imm12R: InstructionOperand = InstructionOperand(
    "imm12",
    OperandKind.Immediate,
    OperandAccess.Read,
    width = 12,
    signed = false,
    pieces = Seq(OperandPiece(21, 10, 0))
  )

  final val imm12ShiftR: InstructionOperand = InstructionOperand(
    "sh",
    OperandKind.Immediate,
    OperandAccess.Read,
    width = 1,
    signed = false,
    pieces = Seq(OperandPiece(22, 22, 0))
  )

  final val imm16R: InstructionOperand = InstructionOperand(
    "imm16",
    OperandKind.Immediate,
    OperandAccess.Read,
    width = 16,
    signed = false,
    pieces = Seq(OperandPiece(20, 5, 0))
  )

  final val hwR: InstructionOperand = InstructionOperand(
    "hw",
    OperandKind.Immediate,
    OperandAccess.Read,
    width = 2,
    signed = false,
    pieces = Seq(OperandPiece(22, 21, 0))
  )

  final protected def sem(
    id: String,
    category: String,
    extension: String = "base",
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

  final protected def a64(
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
    InstructionForm.fixed(
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
