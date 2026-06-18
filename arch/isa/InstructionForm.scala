package arch.isa

import chisel3.util.BitPat

final case class InstructionForm(
  id: String,
  name: String,
  pattern: EncodingPattern,
  mode: String = "",
  format: String = "",
  asm: String = "",
  fields: Seq[InstructionField] = Seq.empty,
  operands: Seq[InstructionOperand] = Seq.empty,
  semantic: InstructionSemantic = InstructionSemantic(id = "unknown"),
  attributes: Seq[String] = Seq.empty,
  aliasOf: Option[String] = None,
  priority: Int = 0,
  description: String = ""
) {
  require(id.nonEmpty, "instruction form id must not be empty")
  require(name.nonEmpty, "instruction form name must not be empty")

  pattern.requireLegal(id)

  def isAlias: Boolean =
    aliasOf.nonEmpty

  def bitPat: BitPat =
    pattern match {
      case p: FixedBitPattern => p.bitPat
      case _                  =>
        throw new UnsupportedOperationException(
          s"instruction form '$id' is not a fixed-bit encoding"
        )
    }

  def fixedWidth: Option[Int] =
    pattern.fixedWidth
}

object InstructionForm {
  def fixed(
    name: String,
    pattern: String,
    mode: String = "",
    format: String = "",
    asm: String = "",
    fields: Seq[InstructionField] = Seq.empty,
    operands: Seq[InstructionOperand] = Seq.empty,
    semantic: InstructionSemantic = InstructionSemantic(id = "unknown"),
    attributes: Seq[String] = Seq.empty,
    aliasOf: Option[String] = None,
    priority: Int = 0,
    description: String = ""
  ): InstructionForm =
    InstructionForm(
      id = name,
      name = name,
      pattern = EncodingPattern.fixed(pattern),
      mode = mode,
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

  def fixedWithId(
    id: String,
    name: String,
    pattern: String,
    mode: String = "",
    format: String = "",
    asm: String = "",
    fields: Seq[InstructionField] = Seq.empty,
    operands: Seq[InstructionOperand] = Seq.empty,
    semantic: InstructionSemantic = InstructionSemantic(id = "unknown"),
    attributes: Seq[String] = Seq.empty,
    aliasOf: Option[String] = None,
    priority: Int = 0,
    description: String = ""
  ): InstructionForm =
    InstructionForm(
      id = id,
      name = name,
      pattern = EncodingPattern.fixed(pattern),
      mode = mode,
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

  def decoder(
    name: String,
    decoder: String,
    key: String,
    minBits: Int,
    maxBits: Int,
    mode: String = "",
    format: String = "",
    asm: String = "",
    operands: Seq[InstructionOperand] = Seq.empty,
    semantic: InstructionSemantic = InstructionSemantic(id = "unknown"),
    attributes: Seq[String] = Seq.empty,
    aliasOf: Option[String] = None,
    priority: Int = 0,
    description: String = ""
  ): InstructionForm =
    InstructionForm(
      id = name,
      name = name,
      pattern = DecoderPattern(decoder = decoder, key = key, minBits = minBits, maxBits = maxBits),
      mode = mode,
      format = format,
      asm = asm,
      operands = operands,
      semantic = semantic,
      attributes = attributes,
      aliasOf = aliasOf,
      priority = priority,
      description = description
    )

  def decoderWithId(
    id: String,
    name: String,
    decoder: String,
    key: String,
    minBits: Int,
    maxBits: Int,
    mode: String = "",
    format: String = "",
    asm: String = "",
    operands: Seq[InstructionOperand] = Seq.empty,
    semantic: InstructionSemantic = InstructionSemantic(id = "unknown"),
    attributes: Seq[String] = Seq.empty,
    aliasOf: Option[String] = None,
    priority: Int = 0,
    description: String = ""
  ): InstructionForm =
    InstructionForm(
      id = id,
      name = name,
      pattern = DecoderPattern(decoder = decoder, key = key, minBits = minBits, maxBits = maxBits),
      mode = mode,
      format = format,
      asm = asm,
      operands = operands,
      semantic = semantic,
      attributes = attributes,
      aliasOf = aliasOf,
      priority = priority,
      description = description
    )
}
