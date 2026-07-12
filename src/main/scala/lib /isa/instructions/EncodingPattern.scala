package arch.isa.instructions

import chisel3.util.BitPat

sealed trait EncodingPattern {
  def kind: String
  def minBits: Int
  def maxBits: Int
  def fixedWidth: Option[Int]
  def isGenericMatchable: Boolean
  def requireLegal(owner: String): Unit
}

final case class FixedBitPattern(
  value: BigInt,
  mask: BigInt,
  width: Int
) extends EncodingPattern {
  require(width > 0, s"fixed bit pattern width must be positive, got $width")
  require(value >= 0, s"fixed bit pattern value must be non-negative")
  require(mask >= 0, s"fixed bit pattern mask must be non-negative")

  override def kind: String                = "fixed_bits"
  override def minBits: Int                = width
  override def maxBits: Int                = width
  override def fixedWidth: Option[Int]     = Some(width)
  override def isGenericMatchable: Boolean = true

  def bitPat: BitPat = {
    val bits = (width - 1 to 0 by -1).map { i =>
      val valueBit = (value >> i) & 1
      val maskBit  = (mask >> i) & 1
      if (maskBit == 1) valueBit.toString else "?"
    }.mkString

    BitPat("b" + bits)
  }

  def matches(instr: BigInt): Boolean =
    ((instr ^ value) & mask) == 0

  def overlaps(that: FixedBitPattern): Boolean =
    width == that.width && (((value ^ that.value) & mask & that.mask) == 0)

  override def requireLegal(owner: String): Unit = {
    val limitMask = (BigInt(1) << width) - 1
    require(value <= limitMask, s"$owner fixed value exceeds width $width")
    require(mask <= limitMask, s"$owner fixed mask exceeds width $width")
    require((value & mask) == value, s"$owner fixed value contains bits outside mask")
  }
}

final case class BytePattern(
  value: Seq[Int],
  mask: Seq[Int],
  minBytes: Int,
  maxBytes: Int
) extends EncodingPattern {
  require(value.nonEmpty, "byte pattern value must not be empty")
  require(
    value.size == mask.size,
    s"byte pattern value/mask size mismatch: ${value.size}/${mask.size}"
  )
  require(minBytes > 0, s"byte pattern minBytes must be positive, got $minBytes")
  require(
    maxBytes >= minBytes,
    s"byte pattern maxBytes must be >= minBytes, got $maxBytes/$minBytes"
  )
  require(value.size <= maxBytes, s"byte pattern size ${value.size} exceeds maxBytes $maxBytes")

  override def kind: String                = "bytes"
  override def minBits: Int                = minBytes * 8
  override def maxBits: Int                = maxBytes * 8
  override def fixedWidth: Option[Int]     = if (minBytes == maxBytes) Some(minBytes * 8) else None
  override def isGenericMatchable: Boolean = true

  def matches(bytes: Seq[Int]): Boolean =
    if (bytes.size < value.size) {
      false
    } else {
      value.indices.forall { i =>
        ((bytes(i) ^ value(i)) & mask(i)) == 0
      }
    }

  override def requireLegal(owner: String): Unit = {
    value.foreach(x => require(x >= 0 && x <= 255, s"$owner byte value out of range: $x"))
    mask.foreach(x => require(x >= 0 && x <= 255, s"$owner byte mask out of range: $x"))

    value.zip(mask).zipWithIndex.foreach { case ((v, m), i) =>
      require((v & m) == v, s"$owner byte $i value contains bits outside mask")
    }
  }
}

final case class DecoderPattern(
  decoder: String,
  key: String,
  minBits: Int,
  maxBits: Int,
  fixedWidth: Option[Int] = None
) extends EncodingPattern {
  require(decoder.nonEmpty, "decoder pattern decoder must not be empty")
  require(key.nonEmpty, "decoder pattern key must not be empty")
  require(minBits > 0, s"decoder pattern minBits must be positive, got $minBits")
  require(maxBits >= minBits, s"decoder pattern maxBits must be >= minBits, got $maxBits/$minBits")
  fixedWidth.foreach(w =>
    require(
      w >= minBits && w <= maxBits,
      s"decoder pattern fixedWidth $w outside $minBits..$maxBits"
    )
  )

  override def kind: String                = "decoder"
  override def isGenericMatchable: Boolean = false

  override def requireLegal(owner: String): Unit = ()
}

object EncodingPattern {
  def fixed(pattern: String): FixedBitPattern = {
    require(pattern.startsWith("b"), s"fixed pattern must start with 'b': $pattern")

    val body = pattern.stripPrefix("b").filter(_ != '_')
    require(body.nonEmpty, s"fixed pattern has no bits: $pattern")

    val value = body.foldLeft(BigInt(0)) { (acc, ch) =>
      ch match {
        case '0' | '?' => acc << 1
        case '1'       => (acc << 1) | 1
        case other     =>
          throw new IllegalArgumentException(s"invalid fixed pattern char '$other' in $pattern")
      }
    }

    val mask = body.foldLeft(BigInt(0)) { (acc, ch) =>
      ch match {
        case '0' | '1' => (acc << 1) | 1
        case '?'       => acc << 1
        case other     =>
          throw new IllegalArgumentException(s"invalid fixed pattern char '$other' in $pattern")
      }
    }

    FixedBitPattern(value = value, mask = mask, width = body.length)
  }

  def fixed(bp: BitPat, width: Int): FixedBitPattern =
    FixedBitPattern(value = bp.value, mask = bp.mask, width = width)
}
