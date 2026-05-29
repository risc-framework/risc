package arch.node.div

import arch.node.fupool.FuIO
import vutils.graph.{ NodeType, NodeDim, NodeDimensionImpl, NodeDimensionRegistry, NodePort }
import chisel3._

object DivMeta {
  val Type = NodeType("div")
  val FU   = NodePort[DivIO, FuIO]("fu", _.fu)
}

object DivDims {
  val ISA = NodeDim("isa")
}

trait DivIsaImpl extends NodeDimensionImpl {
  override def nodeType: NodeType = DivMeta.Type
  override def dim: NodeDim       = DivDims.ISA
  override def name: String       = value

  def decode(uop: UInt): DivCtrl
}

object DivIsaFactory extends NodeDimensionRegistry[DivIsaImpl](DivMeta.Type, DivDims.ISA)

object DivInit {
  val rv32i  = impls.isa.rv32i.DivRv32iIsa
  val rv32im = impls.isa.rv32im.DivRv32imIsa
}
