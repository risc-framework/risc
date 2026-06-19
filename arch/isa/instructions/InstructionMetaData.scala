package arch.isa.instructions

import chisel3._
import chisel3.util.{ Cat, Fill }

object OperandKind {
  val Register  = "reg"
  val Immediate = "imm"
  val Memory    = "mem"
  val Csr       = "csr"
  val Flag      = "flag"
  val Predicate = "predicate"
  val Segment   = "segment"
  val Vector    = "vector"
  val System    = "system"
  val Implicit  = "implicit"
  val Unknown   = "unknown"
}

object OperandAccess {
  val None      = "none"
  val Read      = "read"
  val Write     = "write"
  val ReadWrite = "readwrite"
}

object InstructionCategory {
  val Alu     = "alu"
  val Load    = "load"
  val Store   = "store"
  val Branch  = "branch"
  val Jump    = "jump"
  val Call    = "call"
  val Return  = "return"
  val Csr     = "csr"
  val System  = "system"
  val Mul     = "mul"
  val Div     = "div"
  val Atomic  = "atomic"
  val Float   = "float"
  val Vector  = "vector"
  val Crypto  = "crypto"
  val Simd    = "simd"
  val Fence   = "fence"
  val Unknown = "unknown"
}

object ControlFlowKind {
  val None     = "none"
  val Branch   = "branch"
  val Jump     = "jump"
  val Call     = "call"
  val Return   = "return"
  val Trap     = "trap"
  val Eret     = "eret"
  val Indirect = "indirect"
}

object MemoryAccessKind {
  val None      = "none"
  val Read      = "read"
  val Write     = "write"
  val ReadWrite = "readwrite"
}

object PrivilegeLevel {
  val Any        = "any"
  val User       = "user"
  val Supervisor = "supervisor"
  val Machine    = "machine"
  val Kernel     = "kernel"
  val Hypervisor = "hypervisor"
  val System     = "system"
}

object Endianness {
  val Little = "little"
  val Big    = "big"
  val Mixed  = "mixed"
}

final case class InstructionField(
  name: String,
  hi: Int,
  lo: Int,
  signed: Boolean = false,
  role: String = ""
) {
  require(name.nonEmpty, "field name must not be empty")
  require(hi >= lo, s"field '$name' has invalid range: $hi:$lo")
  require(lo >= 0, s"field '$name' has negative low bit: $lo")

  def width: Int =
    hi - lo + 1
}

final case class OperandPiece(
  srcHi: Int,
  srcLo: Int,
  dstLo: Int
) {
  require(srcHi >= srcLo, s"invalid operand piece source range: $srcHi:$srcLo")
  require(srcLo >= 0, s"invalid operand piece source low bit: $srcLo")
  require(dstLo >= 0, s"invalid operand piece destination low bit: $dstLo")

  def width: Int =
    srcHi - srcLo + 1

  def dstHi: Int =
    dstLo + width - 1
}

final case class InstructionOperand(
  name: String,
  kind: String,
  access: String = OperandAccess.Read,
  width: Int = 0,
  signed: Boolean = false,
  pieces: Seq[OperandPiece] = Seq.empty,
  extractor: String = "",
  display: String = "",
  implicitValue: String = ""
) {
  require(name.nonEmpty, "operand name must not be empty")
  require(width >= 0, s"operand '$name' has negative width: $width")

  pieces.foreach { piece =>
    require(width > 0, s"operand '$name' has piece $piece but operand width is $width")
    require(
      piece.dstHi < width,
      s"operand '$name' piece destination ${piece.dstHi}:${piece.dstLo} exceeds operand width $width"
    )
  }

  def apply(instr: UInt): UInt =
    extract(instr)

  def apply(instr: UInt, targetWidth: Int): UInt =
    extract(instr, targetWidth)

  def extract(instr: UInt): UInt = {
    require(width > 0, s"operand '$name' cannot be extracted because width is $width")
    require(pieces.nonEmpty, s"operand '$name' cannot be extracted because it has no pieces")

    val instrWidth = instr.getWidth
    if (instrWidth > 0) {
      pieces.foreach { piece =>
        require(
          piece.srcHi < instrWidth,
          s"operand '$name' source ${piece.srcHi}:${piece.srcLo} exceeds instruction width $instrWidth"
        )
      }
    }

    val bits = Wire(Vec(width, Bool()))
    for (i <- 0 until width)
      bits(i) := false.B

    pieces.foreach { piece =>
      for (i <- 0 until piece.width)
        bits(piece.dstLo + i) := instr(piece.srcLo + i)
    }

    bits.asUInt
  }

  def extract(instr: UInt, targetWidth: Int): UInt = {
    require(targetWidth > 0, s"operand '$name' targetWidth must be positive, got $targetWidth")
    require(
      targetWidth >= width,
      s"operand '$name' targetWidth $targetWidth is smaller than operand width $width"
    )

    val raw = extract(instr)

    if (targetWidth == width) {
      raw
    } else if (signed) {
      Cat(Fill(targetWidth - width, raw(width - 1)), raw)
    } else {
      Cat(0.U((targetWidth - width).W), raw)
    }
  }
}

final case class InstructionSemantic(
  id: String,
  category: String = InstructionCategory.Unknown,
  extension: String = "",
  privilege: String = PrivilegeLevel.Any,
  controlFlow: String = ControlFlowKind.None,
  memory: String = MemoryAccessKind.None,
  readsPc: Boolean = false,
  writesPc: Boolean = false,
  mayTrap: Boolean = false,
  serializing: Boolean = false,
  notes: String = ""
) {
  require(id.nonEmpty, "semantic id must not be empty")
}
