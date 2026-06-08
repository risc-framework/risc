package arch.core.scheduler

import arch.configs._
import vutils.graph.{ NodeDim, NodeDimensionImpl, NodeDimensionRegistry, NodeType }

object SchedulerMeta {
  val Type = NodeType("scheduler")
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
