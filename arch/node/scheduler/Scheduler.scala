package arch.node.scheduler

import arch.configs._
import arch.node.fupool.FuPool
import vutils.graph.{ Node, NodeConfig, NodeSelector, NodeType }
import chisel3._

class SchedulerIO(implicit p: Parameters) extends Bundle {
  val dispatch = new SchedulerDispatchIO
  val fu       = new SchedulerFuIO
  val ctrl     = new SchedulerCtrlIO
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

  def bind(pool: FuPool): Unit = {
    pool.io.fu.flush := io.ctrl.flush

    for (i <- 0 until p(NumFUs)) {
      pool.io.fu.req(i) <> io.fu.reqs(i)
      io.fu.done(i) := pool.io.fu.done(i)
    }
  }
}
