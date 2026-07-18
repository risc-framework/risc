package arch.core.scheduler

import arch.configs._
import arch.core.fupool.{ FuReq, FuResp }
import arch.core.sb.StoreAddressBundle
import vutils.graph.NodeDims
import chisel3._
import chisel3.util.{ DecoupledIO, ValidIO }

object SchedulerDims extends NodeDims("scheduler") {
  val POLICY = dim("policy")
}

trait SchedulerPolicyImpl extends SchedulerDims.POLICY.Impl {
  def elaborate(
    flush: Bool,
    dispatched: Int => DecoupledIO[FuReq],
    fuReq: Int => DecoupledIO[FuReq],
    fuDone: Int => DecoupledIO[FuResp],
    storeAddr: Int => ValidIO[StoreAddressBundle],
    debug: SchedulerDebugInfo
  )(implicit p: Parameters): Unit
}

class SchedulerDebugInfo extends Bundle {
  val raw_wait         = Bool()
  val waw_wait         = Bool()
  val fu_busy          = Bool()
  val older_lane_block = Bool()
  val no_matching_fu   = Bool()
}

object SchedulerPolicyFactory extends SchedulerDims.POLICY.Registry[SchedulerPolicyImpl]

object SchedulerInit {
  val scoreboard = impls.policy.scoreboard.ScoreboardSchedulerPolicy.registered
  val tomasulo   = impls.policy.tomasulo.TomasuloSchedulerPolicy.registered
}
