package arch.core.regfile

import arch.core.dispatch.DispatchRegfileIO
import arch.core.rob.RobRegfileIO
import vutils.graph.{ NodeDim, NodeDimensionImpl, NodeDimensionRegistry, NodePort, NodeType }

object RegfileMeta {
  val Type     = NodeType("regfile")
  val DISPATCH = NodePort[RegfileIO, DispatchRegfileIO]("dispatch", _.dispatch)
  val ROB      = NodePort[RegfileIO, RobRegfileIO]("rob", _.rob)
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
