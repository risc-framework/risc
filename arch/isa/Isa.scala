package arch.isa

import arch.isa.instructions._
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

final case class Isa(
  name: String,
  family: String,
  xlen: Int,
  ilen: Int,
  numArchRegs: Int,
  modes: Seq[IsaMode],
  instrSet: InstructionSet,
  features: Seq[String] = Seq.empty
) {
  require(name.nonEmpty, "ISA name must not be empty")
  require(family.nonEmpty, s"ISA '$name' family must not be empty")
  require(xlen > 0, s"ISA '$name' has invalid xlen: $xlen")
  require(ilen > 0, s"ISA '$name' has invalid ilen: $ilen")
  require(numArchRegs > 0, s"ISA '$name' has invalid numArchRegs: $numArchRegs")
  require(modes.nonEmpty, s"ISA '$name' must define at least one mode")

  instrSet.validate()

  def defaultMode: IsaMode =
    modes.head

  def isBigEndian: Boolean =
    defaultMode.endianness == Endianness.Big

  def minInstrBits: Int =
    modes.map(_.minInstrBits).min

  def maxInstrBits: Int =
    modes.map(_.maxInstrBits).max

  def hasFixedInstructionWidth: Boolean =
    minInstrBits == maxInstrBits

  def bubble: BitPat =
    instrSet.nop.getOrElse(throw new Exception(s"ISA '$name' has no NOP form defined")).bitPat

  def form(id: String): InstructionForm =
    instrSet.get(id)

  def bitPat(id: String): BitPat =
    form(id).bitPat
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
