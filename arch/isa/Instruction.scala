package arch.isa

import chisel3.util.BitPat

final case class InstructionEncoding(
  name: String,
  value: BigInt,
  mask: BigInt
) {
  def bitPat(width: Int): BitPat = {
    val bits = (width - 1 to 0 by -1).map { i =>
      val valueBit = (value >> i) & 1
      val maskBit  = (mask >> i) & 1

      if (maskBit == 1) valueBit.toString else "?"
    }.mkString

    BitPat("b" + bits)
  }
}

object InstructionEncoding {
  def fromBitPat(name: String, bp: BitPat): InstructionEncoding =
    InstructionEncoding(
      name = name,
      value = bp.value,
      mask = bp.mask
    )
}

final case class InstructionSet(
  nop: Option[InstructionEncoding] = None,
  encodings: Seq[InstructionEncoding] = Seq.empty
) {
  def all: Seq[InstructionEncoding] =
    nop.toSeq ++ encodings

  def get(name: String): InstructionEncoding =
    all
      .find(_.name == name)
      .getOrElse(throw new NoSuchElementException(s"Instruction '$name' not found"))
}
