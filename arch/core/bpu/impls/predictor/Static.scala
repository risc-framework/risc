package arch.core.bpu.impls.predictor

import arch.core.bpu._
import arch.configs._
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._

object StaticNotTakenPredictor extends RegisteredNodeUtils[PredictorKindImpl] {
  override def utils: PredictorKindImpl = new PredictorKindImpl {
    override def value: String = "static_nt"

    override def elaborate(req: PredictorQueryReq, resp: PredictorQueryResp, update: BpuUpdate)(
      implicit p: Parameters
    ): Unit =
      for (w <- 0 until p(IssueWidth)) {
        resp.taken(w)        := false.B
        resp.pht_index(w)    := 0.U
        resp.ghr_snapshot(w) := 0.U
        resp.provider(w)     := 0.U
        resp.alt_taken(w)    := false.B
      }
  }

  override def registry: NodeDimensionRegistry[PredictorKindImpl] =
    PredictorKindFactory
}

object StaticTakenPredictor extends RegisteredNodeUtils[PredictorKindImpl] {
  override def utils: PredictorKindImpl = new PredictorKindImpl {
    override def value: String = "static_t"

    override def elaborate(req: PredictorQueryReq, resp: PredictorQueryResp, update: BpuUpdate)(
      implicit p: Parameters
    ): Unit =
      for (w <- 0 until p(IssueWidth)) {
        resp.taken(w)        := true.B
        resp.pht_index(w)    := 0.U
        resp.ghr_snapshot(w) := 0.U
        resp.provider(w)     := 0.U
        resp.alt_taken(w)    := true.B
      }
  }

  override def registry: NodeDimensionRegistry[PredictorKindImpl] =
    PredictorKindFactory
}
