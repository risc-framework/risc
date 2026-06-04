package arch.isa

import chisel3.util.BitPat
import scala.collection.mutable.LinkedHashMap

final case class Isa(
  name: String,
  xlen: Int,
  ilen: Int,
  numArchRegs: Int,
  isBigEndian: Boolean,
  microOpWidth: Int,
  instrSet: InstructionSet
) {
  def bubble: BitPat =
    instrSet.nop
      .getOrElse(throw new Exception(s"ISA '$name' has no NOP defined"))
      .bitPat(ilen)

  def enc(name: String): InstructionEncoding =
    instrSet.get(name)

  def bitPat(name: String): BitPat =
    enc(name).bitPat(ilen)
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

  def microOpWidth(isa: String): Int =
    get(isa).microOpWidth

  def bubble(isa: String): BitPat =
    get(isa).bubble

  def instrSet(isa: String): InstructionSet =
    get(isa).instrSet
}
