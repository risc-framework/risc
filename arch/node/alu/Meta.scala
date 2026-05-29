package arch.node.alu

import arch.node.uop.MicroOp
import arch.node.fupool.FuIO
import arch.configs._
import vutils.graph.{ NodeType, NodeDim, NodeDimensionImpl, NodeDimensionRegistry, NodePort }
import chisel3._

object AluMeta {
  val Type = NodeType("alu")
  val FU   = NodePort[AluIO, FuIO]("fu", _.fu)
}

object AluDims {
  val ISA = NodeDim("isa")
}

trait AluIsaImpl extends NodeDimensionImpl {
  override def nodeType: NodeType = AluMeta.Type
  override def dim: NodeDim       = AluDims.ISA
  override def name: String       = value

  def fnTypeWidth: Int
  def decode(uop: UInt): AluCtrl
  def execute(uop: MicroOp)(implicit p: Parameters): UInt
}

object AluIsaFactory extends NodeDimensionRegistry[AluIsaImpl](AluMeta.Type, AluDims.ISA)

object AluInit {
  val rv32i  = impls.isa.rv32i.AluRv32iIsa
  val rv32im = impls.isa.rv32im.AluRv32imIsa
}
