package arch.core.decode

import arch.core.dispatch.DispatchDecodeIO
import arch.configs._
import vutils.graph.{ NodeDim, NodeDimensionImpl, NodeDimensionRegistry, NodePort, NodeType }
import chisel3._
import chisel3.util.{ BitPat, DecoupledIO }

case object DecodeKind extends Field[String]("table")

object DecodeMeta {
  val Type     = NodeType("decode")
  val IFU      = NodePort[DecodeIO, Vec[DecoupledIO[DecodePacket]]]("ifu", _.ifu)
  val DISPATCH = NodePort[DecodeIO, DispatchDecodeIO]("dispatch", _.dispatch)
}

object DecodeDims {
  val ISA  = NodeDim("isa")
  val KIND = NodeDim("kind")
}

trait DecodeIsaImpl extends NodeDimensionImpl {
  override def nodeType: NodeType = DecodeMeta.Type
  override def dim: NodeDim       = DecodeDims.ISA
  override def name: String       = value

  def default(implicit p: Parameters): List[BitPat]
  def table(implicit p: Parameters): Array[(BitPat, List[BitPat])]
  def reg(sel: UInt, instr: UInt)(implicit p: Parameters): UInt
  def readable(addr: UInt)(implicit p: Parameters): Bool
  def writable(addr: UInt)(implicit p: Parameters): Bool
  def imm(sel: UInt, instr: UInt)(implicit p: Parameters): UInt
}

trait DecodeKindImpl extends NodeDimensionImpl {
  override def nodeType: NodeType = DecodeMeta.Type
  override def dim: NodeDim       = DecodeDims.KIND
  override def name: String       = value

  def decode(isa: DecodeIsaImpl, in: DecodePacket)(implicit p: Parameters): DecodedPacket
}

object DecodeIsaFactory
    extends NodeDimensionRegistry[DecodeIsaImpl](DecodeMeta.Type, DecodeDims.ISA)
object DecodeKindFactory
    extends NodeDimensionRegistry[DecodeKindImpl](DecodeMeta.Type, DecodeDims.KIND)

object DecodeInit {
  val table  = impls.kind.table.DecodeTableKind
  val rv32i  = impls.isa.rv32i.DecodeRv32iIsa
  val rv32im = impls.isa.rv32im.DecodeRv32imIsa
}
