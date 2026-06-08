package arch.core.bpu

import arch.configs._
import vutils.graph.{ NodeDim, NodeDimensionImpl, NodeDimensionRegistry, NodeType }

object BtbMeta {
  val Type = NodeType("btb")
}

object PredictorMeta {
  val Type = NodeType("predictor")
}

object BpuMeta {
  val Type = NodeType("bpu")
}

object PredictorDims {
  val KIND = NodeDim("kind")
}

trait PredictorKindImpl extends NodeDimensionImpl {
  override def nodeType: NodeType = PredictorMeta.Type
  override def dim: NodeDim       = PredictorDims.KIND
  override def name: String       = value

  def elaborate(io: PredictorIO)(implicit p: Parameters): Unit
}

object PredictorKindFactory
    extends NodeDimensionRegistry[PredictorKindImpl](PredictorMeta.Type, PredictorDims.KIND)

object BpuInit {
  val staticNotTaken = impls.predictor.StaticNotTakenPredictor
  val staticTaken    = impls.predictor.StaticTakenPredictor
  val gshare         = impls.predictor.GSharePredictor
}
