package arch.core.scheduler

import arch.configs._
import vutils.graph.{ Node, NodeConfig, NodeSelector, NodeType }
import chisel3._

class SchedulerIO(implicit p: Parameters) extends Bundle {
  val exception = new SchedulerExceptionIO
  val dispatch  = new SchedulerDispatchIO
  val fu_pool   = new SchedulerFuPoolIO
}

class Scheduler(implicit p: Parameters) extends Node(new SchedulerIO) {
  private val cfg = NodeConfig(
    selector = NodeSelector(
      SchedulerDims.POLICY -> p(ScheduleType)
    )
  )

  override def nodeType: NodeType  = SchedulerMeta.Type
  override def desiredName: String = s"scheduler_${cfg.selector.canonicalName}"

  private val policy = SchedulerPolicyFactory.select(cfg)

  policy.elaborate(io)
}
