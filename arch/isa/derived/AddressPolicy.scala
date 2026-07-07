package arch.isa.derived

import arch.isa._
import arch.isa.instructions._
import chisel3._
import chisel3.util.Cat

trait IsaAddressPolicy { this: Isa =>
  final def instrAlignmentBytes: Int =
    defaultMode.alignmentBytes

  final def dataEndianness: String =
    defaultMode.endianness

  final def isDataBigEndian: Boolean =
    dataEndianness == Endianness.Big

  final def isDataLittleEndian: Boolean =
    dataEndianness == Endianness.Little

  final def instrAlignedAddr(addr: UInt): UInt =
    alignDown(addr, instrAlignmentBytes)

  final def instrOffset(addr: UInt): UInt =
    offsetIn(addr, instrAlignmentBytes)

  final def instrMisaligned(addr: UInt): Bool =
    instrOffset(addr) =/= 0.U

  final def beatAlignedAddr(addr: UInt, beatBytes: Int): UInt =
    alignDown(addr, beatBytes)

  final def byteOffsetInBeat(addr: UInt, beatBytes: Int): UInt =
    offsetIn(addr, beatBytes)

  final def laneOffset(addr: UInt, accessBytes: UInt, beatBytes: Int): UInt = {
    requirePow2(beatBytes, "laneOffset beatBytes")
    byteOffsetInBeat(addr, beatBytes)
  }

  final def crossesBeat(addr: UInt, accessBytes: UInt, beatBytes: Int): Bool = {
    requirePow2(beatBytes, "crossesBeat beatBytes")
    val off = byteOffsetInBeat(addr, beatBytes)
    val sum = off +& accessBytes
    sum > beatBytes.U(sum.getWidth.W)
  }

  final def accessByteIndex(i: Int, accessBytes: Int): Int = {
    require(accessBytes > 0, s"accessBytes must be positive, got $accessBytes")
    require(i >= 0 && i < accessBytes, s"byte index $i outside accessBytes $accessBytes")
    if (isDataBigEndian) accessBytes - 1 - i else i
  }

  final def alignDown(addr: UInt, bytes: Int): UInt = {
    requirePow2(bytes, "alignDown bytes")
    val w = log2CeilConst(bytes)
    if (w == 0) addr else Cat(addr(addr.getWidth - 1, w), 0.U(w.W))
  }

  final def offsetIn(addr: UInt, bytes: Int): UInt = {
    requirePow2(bytes, "offsetIn bytes")
    val w = log2CeilConst(bytes)
    if (w == 0) 0.U(1.W) else addr(w - 1, 0)
  }

  private def requirePow2(value: Int, name: String): Unit = {
    require(value > 0, s"$name must be positive, got $value")
    require((value & (value - 1)) == 0, s"$name must be power-of-two, got $value")
  }

  private def log2CeilConst(x: Int): Int = {
    require(x > 0, s"log2CeilConst input must be positive, got $x")
    if (x <= 1) 0 else 32 - Integer.numberOfLeadingZeros(x - 1)
  }
}
