package arch.core.bpu.impls.predictor

import arch.core.bpu._
import arch.configs._
import vutils.graph.{ NodeRegistry, RegisteredNodeUtils }
import chisel3._

object StaticNotTakenPredictor extends RegisteredNodeUtils[PredictorKindImpl] {
  override def utils: PredictorKindImpl = new PredictorKindImpl {
    override def value: String = "static_nt"

    override def elaborate(io: PredictorIO)(implicit p: Parameters): Unit =
      for (w <- 0 until p(IssueWidth)) {
        io.query.taken(w)        := false.B
        io.query.pht_index(w)    := 0.U
        io.query.ghr_snapshot(w) := 0.U
      }
  }

  override def registry: NodeRegistry[PredictorKindImpl] = PredictorKindFactory
}

object StaticTakenPredictor extends RegisteredNodeUtils[PredictorKindImpl] {
  override def utils: PredictorKindImpl = new PredictorKindImpl {
    override def value: String = "static_t"

    override def elaborate(io: PredictorIO)(implicit p: Parameters): Unit =
      for (w <- 0 until p(IssueWidth)) {
        io.query.taken(w)        := true.B
        io.query.pht_index(w)    := 0.U
        io.query.ghr_snapshot(w) := 0.U
      }
  }

  override def registry: NodeRegistry[PredictorKindImpl] = PredictorKindFactory
}
