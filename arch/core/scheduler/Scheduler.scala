package arch.core.scheduler

import arch.configs._
import arch.core.fupool.FuPool
import vutils.graph.{ Node, NodeConfig, NodeSelector, NodeType }
import chisel3._

class SchedulerIO(implicit p: Parameters) extends Bundle {
  val exception = new SchedulerExceptionIO
  val dispatch  = new SchedulerDispatchIO
  val fu        = new SchedulerFuIO
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

  def bind(pool: FuPool): Unit =
    for (i <- 0 until p(NumFUs)) {
      pool.io.fu.req(i) <> io.fu.reqs(i)
      io.fu.done(i) := pool.io.fu.done(i)
    }
}
