package arch.node.imm

import vutils.graph.{ NodeType, NodeDim, NodeDimensionImpl, NodeDimensionRegistry }
import chisel3._

object ImmMeta {
  val Type = NodeType("alu")
}

object ImmDims {
  val ISA = NodeDim("isa")
}

trait ImmIsaImpl extends NodeDimensionImpl {
  override def nodeType: NodeType = ImmMeta.Type
  override def dim: NodeDim       = ImmDims.ISA
  override def name: String       = value

  def immTypeWidth: Int
  def gen(instr: UInt, immType: UInt): UInt
}

object ImmIsaFactory extends NodeDimensionRegistry[ImmIsaImpl](ImmMeta.Type, ImmDims.ISA)

object ImmInit {
  val rv32i  = impls.isa.rv32i.ImmRv32iIsa
  val rv32im = impls.isa.rv32im.ImmRv32imIsa
}
