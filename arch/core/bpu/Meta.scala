package arch.core.bpu

import arch.configs._
import vutils.graph.NodeDims

object PredictorDims extends NodeDims("predictor") {
  val KIND = dim("kind")
}

trait PredictorKindImpl extends PredictorDims.KIND.Impl {
  def elaborate(req: PredictorQueryReq, resp: PredictorQueryResp, update: BpuUpdate)(implicit
    p: Parameters
  ): Unit
}

object PredictorKindFactory extends PredictorDims.KIND.Registry[PredictorKindImpl]

object BpuInit {
  val gshare   = impls.predictor.GSharePredictor.registered
  val staticNT = impls.predictor.StaticNotTakenPredictor.registered
  val staticT  = impls.predictor.StaticTakenPredictor.registered
}
