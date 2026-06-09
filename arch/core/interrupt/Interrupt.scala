package arch.core.interrupt

import arch.configs._
import arch.core.exception.ExceptionRequest
import vutils.graph.{ Node, NodeConfig, NodeSelector }

class Interrupt(implicit p: Parameters) extends Node[Parameters]("interrupt") {
  override protected def cfg: NodeConfig = NodeConfig(
    selector = NodeSelector(
      InterruptDims.ISA -> p(ISA).name
    )
  )

  val cpu       = in[InterruptCpuReq]
  val fuPool    = in[InterruptFuPoolResp]
  val exception = out[ExceptionRequest]

  private val isaImpl = InterruptIsaFactory.select(cfg)

  exception.out := isaImpl.detect(fuPool.in.view, cpu.in.irq)
}
