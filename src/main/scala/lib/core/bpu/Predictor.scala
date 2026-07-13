package arch.core.bpu

import arch.configs._
import vutils.graph.{ Node, NodeConfig, NodeSelector }

class Predictor(kind: String)(implicit p: Parameters) extends Node[Parameters]("predictor") {
  override protected def cfg: NodeConfig = NodeConfig(
    selector = NodeSelector(
      PredictorDims.KIND -> kind
    )
  )

  val queryReq  = in[PredictorQueryReq]
  val queryResp = out[PredictorQueryResp]
  val update    = in[BpuUpdate]

  private val impl = PredictorKindFactory.select(cfg)

  impl.elaborate(queryReq.in, queryResp.out, update.in)
}
