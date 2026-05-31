package arch.node.scheduler

import arch.configs._
import arch.node.fupool.FuPool
import vutils.graph.{ Node, NodeConfig, NodeSelector, NodeType }

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

import arch.node.alu.AluInit
import arch.node.bru.BruInit
import arch.node.div.DivInit
import arch.node.imm.ImmInit
import arch.node.ld.LdInit
import arch.node.mult.MultInit
import arch.node.regfile.RegfileInit
import arch.node.st.StInit
import vutils._

object SchedulerNode extends App {
  ImmInit
  RegfileInit
  AluInit
  MultInit
  DivInit
  LdInit
  StInit
  BruInit
  SchedulerInit

  DesignEmitter.emit(
    gen = new Scheduler,
    filename = "scheduler",
    target = SystemVerilog,
    info = true,
    lowering = true,
  )
}
