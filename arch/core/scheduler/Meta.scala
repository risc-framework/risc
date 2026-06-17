package arch.core.scheduler

import arch.configs._
import arch.core.fupool.{ FuReq, FuResp }
import vutils.graph.NodeDims
import chisel3.util.DecoupledIO

object SchedulerDims extends NodeDims("scheduler") {
  val POLICY = dim("policy")
}

trait SchedulerPolicyImpl extends SchedulerDims.POLICY.Impl {
  def elaborate(
    exception: SchedulerExceptionReq,
    dispatchReq: Int => DecoupledIO[FuReq],
    fuReq: Int => DecoupledIO[FuReq],
    fuDone: Int => DecoupledIO[FuResp]
  )(implicit p: Parameters): Unit
}

object SchedulerPolicyFactory extends SchedulerDims.POLICY.Registry[SchedulerPolicyImpl]

object SchedulerInit {
  val scoreboard = impls.policy.scoreboard.ScoreboardSchedulerPolicy.registered
  val inorder    = impls.policy.inorder.InorderSchedulerPolicy.registered
}
