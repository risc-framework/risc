package arch.core.bpu

import arch.configs._
import vutils.graph.{ Node, NodeConfig, NodeSelector, NodeType }
import chisel3._

class PredictorIO(implicit p: Parameters) extends Bundle {
  val query  = new PredictorQueryIO
  val update = new BpuUpdateIO
}

class Predictor(kind: String)(implicit p: Parameters) extends Node(new PredictorIO) {
  private val cfg = NodeConfig(
    selector = NodeSelector(
      PredictorDims.KIND -> kind
    )
  )

  override def nodeType: NodeType  = PredictorMeta.Type
  override def desiredName: String = s"predictor_${cfg.selector.canonicalName}"

  private val impl = PredictorKindFactory.select(cfg)

  impl.elaborate(io)
}
