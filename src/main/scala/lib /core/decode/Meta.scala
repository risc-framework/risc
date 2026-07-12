package arch.core.decode

import arch.configs._
import vutils.graph.NodeDims
import chisel3._
import chisel3.util.BitPat

object DecodeDims extends NodeDims("decode") {
  val ISA  = dim("isa")
  val KIND = dim("kind")
}

trait DecodeIsaImpl extends DecodeDims.ISA.Impl {
  def uopWidth: Int
  def default(implicit p: Parameters): List[BitPat]
  def table(implicit p: Parameters): Array[(BitPat, List[BitPat])]
  def reg(sel: UInt, instr: UInt)(implicit p: Parameters): UInt
  def readable(addr: UInt)(implicit p: Parameters): Bool
  def writable(addr: UInt)(implicit p: Parameters): Bool
  def imm(sel: UInt, instr: UInt)(implicit p: Parameters): UInt
}

trait DecodeKindImpl extends DecodeDims.KIND.Impl {
  def decode(isa: DecodeIsaImpl, in: DecodePacket)(implicit p: Parameters): DecodedPacket
}

object DecodeIsaFactory extends DecodeDims.ISA.Registry[DecodeIsaImpl]

object DecodeKindFactory extends DecodeDims.KIND.Registry[DecodeKindImpl]

object DecodeInit {
  val table  = impls.kind.table.DecodeTableKind.registered
  val rv32i  = impls.isa.rv32i.DecodeRv32iIsa.registered
  val rv32im = impls.isa.rv32im.DecodeRv32imIsa.registered
}
