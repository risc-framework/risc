package arch.core.interrupt

import vutils.graph.{ NodeDim, NodeDimensionImpl, NodeDimensionRegistry, NodeType }

object InterruptMeta {
  val Type = NodeType("interrupt")
}

object InterruptDims {
  val ISA = NodeDim("isa")
}

trait InterruptIsaImpl extends NodeDimensionImpl {
  override def nodeType: NodeType = InterruptMeta.Type
  override def dim: NodeDim       = InterruptDims.ISA
  override def name: String       = value

  def detect(
    view: arch.core.csr.CsrTrapView,
    irq: arch.core.csr.InterruptLines
  )(implicit p: arch.configs.Parameters): TrapCandidate
}

object InterruptIsaFactory
    extends NodeDimensionRegistry[InterruptIsaImpl](InterruptMeta.Type, InterruptDims.ISA)

object InterruptInit {
  val rv32i  = impls.isa.rv32i.InterruptRv32iIsa
  val rv32im = impls.isa.rv32im.InterruptRv32imIsa
}
