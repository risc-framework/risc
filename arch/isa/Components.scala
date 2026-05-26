package arch.isa

import chisel3.util.BitPat
import scala.collection.mutable.LinkedHashMap

final case class InstructionEncoding(
  name: String,
  value: BigInt,
  mask: BigInt
)

final case class InstructionSet(
  nop: Option[InstructionEncoding] = None,
  encodings: Seq[InstructionEncoding] = Seq.empty
) {
  def all: Seq[InstructionEncoding] =
    nop.toSeq ++ encodings
}

final case class Isa(
  name: String,
  xlen: Int,
  ilen: Int,
  numArchRegs: Int,
  isBigEndian: Boolean,
  microOpWidth: Int,
  instrSet: Option[InstructionSet] = None
)

abstract class IsaWrapper {
  def isa: Isa
  final def name: String         = isa.name
  final def xlen: Int            = isa.xlen.toInt
  final def ilen: Int            = isa.ilen.toInt
  final def numArchRegs: Int     = isa.numArchRegs.toInt
  final def isBigEndian: Boolean = isa.isBigEndian
  final def microOpWidth: Int    = isa.microOpWidth

  final def bubble: BitPat           = {
    val nop = isa.instrSet
      .flatMap(_.nop)
      .getOrElse(throw new Exception(s"ISA '$name' has no NOP defined"))

    val bits = (ilen - 1 to 0 by -1).map { i =>
      val valueBit = (nop.value >> i) & 1
      val maskBit  = (nop.mask >> i) & 1
      if (maskBit == 1) valueBit.toString else "?"
    }.mkString

    BitPat("b" + bits)
  }
  final def instrSet: InstructionSet = isa.instrSet.get
}

object IsaFactory {
  private val registry = LinkedHashMap.empty[String, IsaWrapper]

  def register(isa: IsaWrapper): Unit = {
    require(!registry.contains(isa.name), s"ISA '${isa.name}' already registered")
    registry(isa.name) = isa
  }

  def fromString(name: String): Option[IsaWrapper] =
    registry.get(name.toLowerCase)

  def available: Seq[IsaWrapper] = registry.values.toSeq

  private def get(name: String): IsaWrapper =
    fromString(name).getOrElse(
      throw new Exception(
        s"Unknown Isa: '$name'. Available: ${available.map(_.name).mkString(", ")}"
      )
    )

  def isa(isa: String): Isa                 = get(isa).isa
  def xlen(isa: String): Int                = get(isa).xlen
  def ilen(isa: String): Int                = get(isa).ilen
  def numArchRegs(isa: String): Int         = get(isa).numArchRegs
  def isBigEndian(isa: String): Boolean     = get(isa).isBigEndian
  def microOpWidth(isa: String): Int        = get(isa).microOpWidth
  def bubble(isa: String): BitPat           = get(isa).bubble
  def instrSet(isa: String): InstructionSet = get(isa).instrSet
}
