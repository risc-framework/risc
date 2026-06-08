package arch.core.mult

import vutils.graph.{ NodeType, NodeDim, NodeDimensionImpl, NodeDimensionRegistry }
import chisel3._

object MultMeta {
  val Type = NodeType("mult")
}

object MultDims {
  val ISA = NodeDim("isa")
}

trait MultIsaImpl extends NodeDimensionImpl {
  override def nodeType: NodeType = MultMeta.Type
  override def dim: NodeDim       = MultDims.ISA
  override def name: String       = value

  def decode(uop: UInt): MultCtrl
}

object MultIsaFactory extends NodeDimensionRegistry[MultIsaImpl](MultMeta.Type, MultDims.ISA)

object MultInit {
  val rv32i  = impls.isa.rv32i.MultRv32iIsa
  val rv32im = impls.isa.rv32im.MultRv32imIsa
}
