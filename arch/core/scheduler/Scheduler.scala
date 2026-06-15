package arch.core.scheduler

import arch.configs._
import arch.core.fupool.{ FuReq, FuResp }
import vutils.graph.{ Node, NodeConfig, NodeSelector }

class Scheduler(implicit p: Parameters) extends Node[Parameters]("scheduler") {
  override protected def cfg: NodeConfig = NodeConfig(
    selector = NodeSelector(
      SchedulerDims.POLICY -> p(ScheduleType)
    )
  )

  val exception   = in[SchedulerExceptionReq]
  val dispatchReq = inDVec[FuReq](p => p(IssueWidth))
  val fuReq       = outDVec[FuReq](p => p(NumFUs))
  val fuDone      = inDVec[FuResp](p => p(NumFUs))

  private val policy = SchedulerPolicyFactory.select(cfg)

  policy.elaborate(
    exception.in,
    w => dispatchReq.in.lanes(w),
    i => fuReq.out.lanes(i),
    i => fuDone.in.lanes(i)
  )
}
