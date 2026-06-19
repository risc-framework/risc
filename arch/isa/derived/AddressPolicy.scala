package arch.isa.derived

import arch.isa._
import arch.isa.instructions._
import chisel3._
import chisel3.util.Cat

trait IsaAddressPolicy { this: Isa =>
  def dataEndianness: String =
    modes.head.endianness

  final def isDataBigEndian: Boolean =
    dataEndianness == Endianness.Big

  final def isDataLittleEndian: Boolean =
    dataEndianness == Endianness.Little

  final def byteOffset(addr: UInt, beatBytes: Int): UInt = {
    requirePow2(beatBytes, "byteOffset beatBytes")

    val offsetWidth = log2CeilConst(beatBytes)

    if (offsetWidth == 0) {
      0.U(1.W)
    } else {
      addr(offsetWidth - 1, 0)
    }
  }

  final def alignedAddr(addr: UInt, beatBytes: Int): UInt = {
    requirePow2(beatBytes, "alignedAddr beatBytes")

    val offsetWidth = log2CeilConst(beatBytes)

    if (offsetWidth == 0) {
      addr
    } else {
      Cat(addr(addr.getWidth - 1, offsetWidth), 0.U(offsetWidth.W))
    }
  }

  final def alignDown(addr: UInt, bytes: Int): UInt = {
    requirePow2(bytes, "alignDown bytes")

    val offsetWidth = log2CeilConst(bytes)

    if (offsetWidth == 0) {
      addr
    } else {
      Cat(addr(addr.getWidth - 1, offsetWidth), 0.U(offsetWidth.W))
    }
  }

  final def offsetIn(addr: UInt, bytes: Int): UInt = {
    requirePow2(bytes, "offsetIn bytes")

    val offsetWidth = log2CeilConst(bytes)

    if (offsetWidth == 0) {
      0.U(1.W)
    } else {
      addr(offsetWidth - 1, 0)
    }
  }

  final def crossesBeat(addr: UInt, accessBytes: UInt, beatBytes: Int): Bool = {
    requirePow2(beatBytes, "crossesBeat beatBytes")

    val w   = log2CeilConst(beatBytes) + 1
    val off = byteOffset(addr, beatBytes)

    (off +& accessBytes)(w - 1, 0) > beatBytes.U(w.W)
  }

  final def laneOffset(addr: UInt, accessBytes: UInt, beatBytes: Int): UInt = {
    requirePow2(beatBytes, "laneOffset beatBytes")
    byteOffset(addr, beatBytes)
  }

  final def bigEndianAccessByteIndex(i: Int, accessBytes: Int): Int = {
    require(
      accessBytes > 0,
      s"bigEndianAccessByteIndex accessBytes must be positive, got $accessBytes"
    )
    require(
      i >= 0 && i < accessBytes,
      s"bigEndianAccessByteIndex index $i outside accessBytes $accessBytes"
    )
    accessBytes - 1 - i
  }

  final def littleEndianAccessByteIndex(i: Int, accessBytes: Int): Int = {
    require(
      accessBytes > 0,
      s"littleEndianAccessByteIndex accessBytes must be positive, got $accessBytes"
    )
    require(
      i >= 0 && i < accessBytes,
      s"littleEndianAccessByteIndex index $i outside accessBytes $accessBytes"
    )
    i
  }

  final def accessByteIndex(i: Int, accessBytes: Int): Int =
    if (isDataBigEndian) bigEndianAccessByteIndex(i, accessBytes)
    else littleEndianAccessByteIndex(i, accessBytes)

  private def requirePow2(value: Int, name: String): Unit = {
    require(value > 0, s"$name must be positive, got $value")
    require((value & (value - 1)) == 0, s"$name must be power-of-two, got $value")
  }

  private def log2CeilConst(x: Int): Int = {
    require(x > 0, s"log2CeilConst input must be positive, got $x")
    if (x <= 1) 0 else 32 - Integer.numberOfLeadingZeros(x - 1)
  }
}
