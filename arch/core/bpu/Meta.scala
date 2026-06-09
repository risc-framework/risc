package arch.core.bpu

import arch.configs._
import vutils.graph.NodeDims

object BpuDims extends NodeDims("bpu") {
  val PREDICTOR = dim("predictor")
}

object BtbDims extends NodeDims("btb")

object PredictorDims extends NodeDims("predictor") {
  val KIND = dim("kind")
}

trait PredictorKindImpl extends PredictorDims.KIND.Impl {
  def elaborate(
    req: PredictorQueryReq,
    resp: PredictorQueryResp,
    update: BpuUpdate
  )(implicit p: Parameters): Unit
}

object PredictorKindFactory extends PredictorDims.KIND.Registry[PredictorKindImpl]

object BpuInit {
  val staticNotTaken = impls.predictor.StaticNotTakenPredictor.registered
  val staticTaken    = impls.predictor.StaticTakenPredictor.registered
  val gshare         = impls.predictor.GSharePredictor.registered
}
