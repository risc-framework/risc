package arch.isa

abstract class IsaDefinition {
  def name: String
  def family: String
  def xlen: Int
  def ilen: Int
  def numArchRegs: Int
  def modes: Seq[IsaMode]
  def nop: Option[InstructionForm]
  def forms: Seq[InstructionForm]
  def features: Seq[String] = Seq.empty

  protected def checkFixedOverlaps: Boolean =
    true

  final def instrSet: InstructionSet =
    InstructionSet(nop = nop, forms = forms)

  protected def validateDefinition(): Unit = {
    require(name.nonEmpty, "ISA definition name must not be empty")
    require(family.nonEmpty, s"ISA '$name' family must not be empty")
    require(xlen > 0, s"ISA '$name' has invalid xlen: $xlen")
    require(ilen > 0, s"ISA '$name' has invalid ilen: $ilen")
    require(numArchRegs > 0, s"ISA '$name' has invalid numArchRegs: $numArchRegs")
    require(modes.nonEmpty, s"ISA '$name' must define at least one mode")

    instrSet.validate(checkFixedOverlaps)
  }

  final def build: Isa = {
    validateDefinition()

    Isa(
      name = name,
      family = family,
      xlen = xlen,
      ilen = ilen,
      numArchRegs = numArchRegs,
      modes = modes,
      instrSet = instrSet,
      features = features
    )
  }

  final lazy val isa: Isa =
    IsaFactory.register(build)
}

abstract class FixedWidthIsaDefinition extends IsaDefinition {
  def instrBits: Int
  def endian: String

  final override def ilen: Int =
    instrBits

  final override def modes: Seq[IsaMode] =
    Seq(
      IsaMode(
        name = "default",
        minInstrBits = instrBits,
        maxInstrBits = instrBits,
        endianness = endian,
        alignmentBytes = instrBits / 8
      )
    )

  final protected def fixed(
    name: String,
    pattern: String,
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
    InstructionForm.fixed(
      name = name,
      pattern = pattern,
      mode = "default",
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
