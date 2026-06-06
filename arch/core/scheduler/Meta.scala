package arch.core.scheduler

import arch.configs._
import vutils.graph.{ NodeDim, NodeDimensionImpl, NodeDimensionRegistry, NodePort, NodeType }

object SchedulerMeta {
  val Type      = NodeType("scheduler")
  val EXCEPTION = NodePort[SchedulerIO, SchedulerExceptionIO]("exception", _.exception)
  val DISPATCH  = NodePort[SchedulerIO, SchedulerDispatchIO]("dispatch", _.dispatch)
  val FU_POOL   = NodePort[SchedulerIO, SchedulerFuPoolIO]("fu_pool", _.fu_pool)
}

object SchedulerDims {
  val POLICY = NodeDim("policy")
}

trait SchedulerPolicyImpl extends NodeDimensionImpl {
  override def nodeType: NodeType = SchedulerMeta.Type
  override def dim: NodeDim       = SchedulerDims.POLICY
  override def name: String       = value

  def elaborate(io: SchedulerIO)(implicit p: Parameters): Unit
}

object SchedulerPolicyFactory
    extends NodeDimensionRegistry[SchedulerPolicyImpl](SchedulerMeta.Type, SchedulerDims.POLICY)

object SchedulerInit {
  val inorder    = impls.policy.inorder.InorderSchedulerPolicy
  val scoreboard = impls.policy.scoreboard.ScoreboardSchedulerPolicy
}
