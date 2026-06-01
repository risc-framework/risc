package arch.node.ld

import arch.node.fupool.FuIO
import arch.configs._
import vutils.graph.{ NodeDim, NodeDimensionImpl, NodeDimensionRegistry, NodePort, NodeType }
import chisel3._

object LdMeta {
  val Type = NodeType("ld")
  val FU   = NodePort[LdIO, FuIO]("fu", _.fu)
  val MEM  = NodePort[LdIO, LdMemIO]("mem", _.mem)
  val SB   = NodePort[LdIO, LdSbFwdIO]("sb", _.sb)
}

object LdDims {
  val ISA = NodeDim("isa")
}

trait LdIsaImpl extends NodeDimensionImpl with LoadDataHelpers {
  override def nodeType: NodeType = LdMeta.Type
  override def dim: NodeDim       = LdDims.ISA
  override def name: String       = value

  def decodeLoad(uop: UInt)(implicit p: Parameters): LoadCtrl
}

object LdIsaFactory extends NodeDimensionRegistry[LdIsaImpl](LdMeta.Type, LdDims.ISA)

object LdInit {
  val rv32i  = impls.isa.rv32i.LdRv32iIsa
  val rv32im = impls.isa.rv32im.LdRv32imIsa
}
