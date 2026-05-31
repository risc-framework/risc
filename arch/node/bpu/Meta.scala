package arch.node.bpu

import arch.configs._
import vutils.graph.{ NodeDim, NodeDimensionImpl, NodeDimensionRegistry, NodePort, NodeType }

case object BpuPredictorKinds extends Field[Seq[String]](Seq("gshare"))

object BtbMeta {
  val Type   = NodeType("btb")
  val QUERY  = NodePort[BtbIO, BtbQueryIO]("query", _.query)
  val UPDATE = NodePort[BtbIO, BpuUpdateIO]("update", _.update)
}

object PredictorMeta {
  val Type   = NodeType("predictor")
  val QUERY  = NodePort[PredictorIO, PredictorQueryIO]("query", _.query)
  val UPDATE = NodePort[PredictorIO, BpuUpdateIO]("update", _.update)
}

object BpuMeta {
  val Type   = NodeType("bpu")
  val FETCH  = NodePort[BpuIO, BpuFetchIO]("fetch", _.fetch)
  val UPDATE = NodePort[BpuIO, BpuUpdateIO]("update", _.update)
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
