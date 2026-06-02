package arch.core.interrupt

import arch.configs._
import arch.core.csr.{ CsrTrapView, InterruptLines }
import vutils.graph.{ Node, NodeConfig, NodeSelector, NodeType }
import chisel3._

class InterruptIO(implicit p: Parameters) extends Bundle {
  val view = Input(new CsrTrapView)
  val irq  = Input(new InterruptLines)
  val out  = Output(new TrapCandidate)
}

class Interrupt(implicit p: Parameters) extends Node(new InterruptIO) {
  private val cfg = NodeConfig(
    selector = NodeSelector(
      InterruptDims.ISA -> p(ISA).name
    )
  )

  override def nodeType: NodeType  = InterruptMeta.Type
  override def desiredName: String = s"interrupt_${cfg.selector.canonicalName}"

  private val isaImpl = InterruptIsaFactory.select(cfg)

  io.out := isaImpl.detect(io.view, io.irq)
}
