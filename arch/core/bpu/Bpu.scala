package arch.core.bpu

import arch.configs._
import vutils.graph.{ Node, NodeConfig, NodeSelector, NodeType }
import chisel3._

class BpuIO(implicit p: Parameters) extends Bundle {
  val ifu = new BpuIfuIO
  val rob = new BpuRobIO
}

class Bpu(implicit p: Parameters) extends Node(new BpuIO) {
  private val cfg = NodeConfig(
    selector = NodeSelector(
      PredictorDims.KIND -> p(BpuPredictorKind)
    )
  )

  override def nodeType: NodeType  = BpuMeta.Type
  override def desiredName: String = s"bpu_${cfg.selector.canonicalName}"

  private val btb       = Module(new Btb)
  private val predictor = Module(new Predictor(p(BpuPredictorKind)))

  btb.io.query.pc      := io.ifu.query_pc
  btb.io.update.update := io.rob.update

  predictor.io.query.pc      := io.ifu.query_pc
  predictor.io.query.accept  := io.ifu.advance_valid && !io.ifu.flush
  predictor.io.query.flush   := io.ifu.flush
  predictor.io.update.update := io.rob.update

  private val rawTaken           = Wire(Vec(p(IssueWidth), Bool()))
  private val killedByOlderTaken = Wire(Vec(p(IssueWidth), Bool()))
  private val branchMask         = Wire(Vec(p(IssueWidth), Bool()))

  killedByOlderTaken(0) := false.B

  for (w <- 0 until p(IssueWidth)) {
    rawTaken(w) := btb.io.query.hit(w) && predictor.io.query.taken(w)

    if (w > 0)
      killedByOlderTaken(w) := killedByOlderTaken(w - 1) || rawTaken(w - 1)
  }

  for (w <- 0 until p(IssueWidth)) {
    io.ifu.taken(w)        := rawTaken(w) && !killedByOlderTaken(w)
    io.ifu.target(w)       := Mux(
      io.ifu.taken(w),
      btb.io.query.entry_out(w).target,
      io.ifu.query_pc(w) + p(PCStep).U
    )
    branchMask(w)          := btb.io.query.hit(w) && !killedByOlderTaken(w)
    io.ifu.pht_index(w)    := predictor.io.query.pht_index(w)
    io.ifu.ghr_snapshot(w) := predictor.io.query.ghr_snapshot(w)
  }

  predictor.io.query.is_branch := branchMask
}
