package arch.isa

import arch.isa.instructions._
import arch.isa.derived._
import chisel3.util.BitPat
import scala.collection.mutable.LinkedHashMap

final case class IsaMode(
  name: String,
  minInstrBits: Int,
  maxInstrBits: Int,
  endianness: String = Endianness.Little,
  alignmentBytes: Int = 1,
  decoder: String = "generic"
) {
  require(name.nonEmpty, "ISA mode name must not be empty")
  require(minInstrBits > 0, s"ISA mode '$name' has invalid minInstrBits: $minInstrBits")
  require(
    maxInstrBits >= minInstrBits,
    s"ISA mode '$name' has invalid maxInstrBits: $maxInstrBits/$minInstrBits"
  )
  require(alignmentBytes > 0, s"ISA mode '$name' has invalid alignmentBytes: $alignmentBytes")

  def fixedWidth: Option[Int] =
    if (minInstrBits == maxInstrBits) Some(minInstrBits) else None
}

abstract class Isa extends IsaAddressPolicy {
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

  final lazy val instrSet: InstructionSet =
    InstructionSet(nop = nop, forms = forms)

  final def isa: Isa =
    this

  final def defaultMode: IsaMode =
    modes.head

  final def isBigEndian: Boolean =
    defaultMode.endianness == Endianness.Big

  final def isLittleEndian: Boolean =
    defaultMode.endianness == Endianness.Little

  final def minInstrBits: Int =
    modes.map(_.minInstrBits).min

  final def maxInstrBits: Int =
    modes.map(_.maxInstrBits).max

  final def hasFixedInstructionWidth: Boolean =
    minInstrBits == maxInstrBits

  final def bubble: BitPat =
    instrSet.nop.getOrElse(throw new Exception(s"ISA '$name' has no NOP form defined")).bitPat

  final def form(id: String): InstructionForm =
    instrSet.get(id)

  final def bitPat(id: String): BitPat =
    form(id).bitPat

  final def validate(): Unit = {
    require(name.nonEmpty, "ISA name must not be empty")
    require(family.nonEmpty, s"ISA '$name' family must not be empty")
    require(xlen > 0, s"ISA '$name' has invalid xlen: $xlen")
    require(ilen > 0, s"ISA '$name' has invalid ilen: $ilen")
    require(numArchRegs > 0, s"ISA '$name' has invalid numArchRegs: $numArchRegs")
    require(modes.nonEmpty, s"ISA '$name' must define at least one mode")

    instrSet.validate(checkFixedOverlaps)
  }

  final lazy val registered: Isa = {
    validate()
    IsaFactory.register(this)
  }
}

abstract class FixedWidthIsa extends Isa {
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
    id: String,
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
      id = id,
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

object IsaFactory {
  private val registry = LinkedHashMap.empty[String, Isa]

  def register(isa: Isa): Isa = {
    val key = isa.name.toLowerCase
    require(!registry.contains(key), s"ISA '${isa.name}' already registered")
    registry(key) = isa
    isa
  }

  def fromString(name: String): Option[Isa] =
    registry.get(name.toLowerCase)

  def get(name: String): Isa =
    fromString(name).getOrElse(
      throw new Exception(
        s"Unknown ISA: '$name'. Available: ${available.map(_.name).mkString(", ")}"
      )
    )

  def available: Seq[Isa] =
    registry.values.toSeq

  def xlen(isa: String): Int =
    get(isa).xlen

  def ilen(isa: String): Int =
    get(isa).ilen

  def numArchRegs(isa: String): Int =
    get(isa).numArchRegs

  def isBigEndian(isa: String): Boolean =
    get(isa).isBigEndian

  def bubble(isa: String): BitPat =
    get(isa).bubble

  def instrSet(isa: String): InstructionSet =
    get(isa).instrSet
}
