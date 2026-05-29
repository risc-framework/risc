package arch.node.ld

import arch.node.fupool.FuIO
import vutils.graph.{ NodeType, NodeDim, NodeDimensionImpl, NodeDimensionRegistry, NodePort }

object LdMeta {
  val Type = NodeType("ld")
  val FU   = NodePort[LdIO, FuIO]("fu", _.fu)
  val MEM  = NodePort[LdIO, LdMemIO]("mem", _.mem)
  val SB   = NodePort[LdIO, LdSbIO]("sb", _.sb)
}

object LdDims {
  val ISA = NodeDim("isa")
}

trait LdIsaImpl extends NodeDimensionImpl {
  override def nodeType: NodeType = LdMeta.Type
  override def dim: NodeDim       = LdDims.ISA
  override def name: String       = value
}

object LdIsaFactory extends NodeDimensionRegistry[LdIsaImpl](LdMeta.Type, LdDims.ISA)

object LdInit {}
