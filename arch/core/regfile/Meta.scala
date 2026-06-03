package arch.core.regfile

import vutils.graph.{ NodeDim, NodeDimensionImpl, NodeDimensionRegistry, NodePort, NodeType }

object RegfileMeta {
  val Type  = NodeType("regfile")
  val READ  = NodePort[RegfileIO, RegfileReadIO]("read", _.read)
  val WRITE = NodePort[RegfileIO, RegfileWriteIO]("write", _.write)
  val DEBUG = NodePort[RegfileIO, RegfileDebugIO]("debug", _.debug)
}

object RegfileDims {
  val ISA = NodeDim("isa")
}

trait RegfileIsaImpl extends NodeDimensionImpl {
  override def nodeType: NodeType = RegfileMeta.Type
  override def dim: NodeDim       = RegfileDims.ISA
  override def name: String       = value

  def initValue(addr: Int): BigInt = 0
  def regName(addr: Int): String   = s"x$addr"
}

object RegfileIsaFactory
    extends NodeDimensionRegistry[RegfileIsaImpl](RegfileMeta.Type, RegfileDims.ISA)

object RegfileInit {
  val rv32i  = impls.isa.rv32i.RegfileRv32iIsa
  val rv32im = impls.isa.rv32im.RegfileRv32imIsa
}
