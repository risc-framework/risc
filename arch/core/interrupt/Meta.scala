package arch.core.interrupt

import arch.configs._
import arch.core.csr.{ CsrTrapView, InterruptLines }
import vutils.graph.{ NodeDim, NodeDimensionImpl, NodeDimensionRegistry, NodePort, NodeType }

object InterruptMeta {
  val Type = NodeType("interrupt")
  val VIEW = NodePort[InterruptIO, CsrTrapView]("view", _.view)
  val IRQ  = NodePort[InterruptIO, InterruptLines]("irq", _.irq)
  val OUT  = NodePort[InterruptIO, TrapCandidate]("out", _.out)
}

object InterruptDims {
  val ISA = NodeDim("isa")
}

trait InterruptIsaImpl extends NodeDimensionImpl {
  override def nodeType: NodeType = InterruptMeta.Type
  override def dim: NodeDim       = InterruptDims.ISA
  override def name: String       = value

  def detect(view: CsrTrapView, irq: InterruptLines)(implicit p: Parameters): TrapCandidate
}

object InterruptIsaFactory
    extends NodeDimensionRegistry[InterruptIsaImpl](InterruptMeta.Type, InterruptDims.ISA)

object InterruptInit {
  val rv32i  = impls.isa.rv32i.InterruptRv32iIsa
  val rv32im = impls.isa.rv32im.InterruptRv32imIsa
}
