package arch.core.interrupt

import arch.configs._
import arch.core.fupool.FuPoolInterruptIO
import vutils.graph.{ Node, NodeConfig, NodeSelector, NodeType }
import chisel3._

class InterruptIO(implicit p: Parameters) extends Bundle {
  val cpu       = new InterruptCpuIO
  val fu_pool   = Flipped(new FuPoolInterruptIO)
  val exception = new InterruptExceptionIO
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

  io.exception.request := isaImpl.detect(io.fu_pool.view, io.cpu.irq)
}
