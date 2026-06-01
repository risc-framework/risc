package arch.node.decoder

import arch.configs._
import vutils.graph.{ NodeDim, NodeDimensionImpl, NodeDimensionRegistry, NodePort, NodeType }
import chisel3._
import chisel3.util.BitPat

case object DecoderKind extends Field[String]("full")

object DecoderMeta {
  val Type   = NodeType("decoder")
  val DECODE = NodePort[DecoderIO, DecoderDecodeIO]("decode", _.decode)
}

object DecoderDims {
  val ISA  = NodeDim("isa")
  val KIND = NodeDim("kind")
}

trait DecoderIsaImpl extends NodeDimensionImpl {
  override def nodeType: NodeType = DecoderMeta.Type
  override def dim: NodeDim       = DecoderDims.ISA
  override def name: String       = value

  def default(implicit p: Parameters): List[BitPat]
  def table(implicit p: Parameters): Array[(BitPat, List[BitPat])]
}

trait DecoderKindImpl extends NodeDimensionImpl {
  override def nodeType: NodeType = DecoderMeta.Type
  override def dim: NodeDim       = DecoderDims.KIND
  override def name: String       = value

  def decode(isa: DecoderIsaImpl, instr: UInt)(implicit p: Parameters): DecodedOutput
}

object DecoderIsaFactory
    extends NodeDimensionRegistry[DecoderIsaImpl](DecoderMeta.Type, DecoderDims.ISA)
object DecoderKindFactory
    extends NodeDimensionRegistry[DecoderKindImpl](DecoderMeta.Type, DecoderDims.KIND)

object DecoderInit {
  val full   = impls.kind.FullDecoderKind
  val rv32i  = impls.isa.rv32i.DecoderRv32iIsa
  val rv32im = impls.isa.rv32im.DecoderRv32imIsa
}
