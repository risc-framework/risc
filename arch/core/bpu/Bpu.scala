package arch.core.bpu

import arch.configs._
import vutils.graph.{ Node, NodeType }
import chisel3._

class BpuIO(implicit p: Parameters) extends Bundle {
  val ifu = new BpuIfuIO
  val rob = new BpuRobIO
}

class Bpu(implicit p: Parameters) extends Node(new BpuIO) {
  override def nodeType: NodeType  = BpuMeta.Type
  override def desiredName: String = s"bpu_${p(ISA).name}"

  private val predictorKinds = p(BpuPredictorKinds)

  require(predictorKinds.nonEmpty, "BpuPredictorKinds must contain at least one predictor kind")

  private val btb        = Module(new Btb)
  private val predictors = predictorKinds.map(kind => Module(new Predictor(kind)))
  private val selected   = predictors.last

  btb.io.query.pc      := io.ifu.query_pc
  btb.io.update.update := io.rob.update

  for (pred <- predictors) {
    pred.io.query.pc      := io.ifu.query_pc
    pred.io.query.accept  := io.ifu.advance_valid && !io.ifu.flush
    pred.io.query.flush   := io.ifu.flush
    pred.io.update.update := io.rob.update
  }

  private val rawTaken           = Wire(Vec(p(IssueWidth), Bool()))
  private val killedByOlderTaken = Wire(Vec(p(IssueWidth), Bool()))
  private val branchMask         = Wire(Vec(p(IssueWidth), Bool()))

  killedByOlderTaken(0) := false.B

  for (w <- 0 until p(IssueWidth)) {
    rawTaken(w) := btb.io.query.hit(w) && selected.io.query.taken(w)

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
    io.ifu.pht_index(w)    := selected.io.query.pht_index(w)
    io.ifu.ghr_snapshot(w) := selected.io.query.ghr_snapshot(w)
  }

  for (pred <- predictors)
    pred.io.query.is_branch := branchMask
}
