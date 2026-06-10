package arch.core.bpu

import arch.configs._
import chisel3._
import vutils.graph.Node

class Bpu(implicit p: Parameters) extends Node[Parameters]("bpu") {
  val ifuReq    = in[BpuIfuReq]
  val ifuResp   = out[BpuIfuResp]
  val robUpdate = in[BpuUpdate]

  private val btb       = subnode(new Btb)
  private val predictor = subnode(new Predictor(p(BpuPredictorKind)))

  btb.queryReq.in.pc := ifuReq.in.query_pc
  btb.update.in      := robUpdate.in

  predictor.queryReq.in.pc     := ifuReq.in.query_pc
  predictor.queryReq.in.accept := ifuReq.in.advance_valid && !ifuReq.in.flush
  predictor.queryReq.in.flush  := ifuReq.in.flush
  predictor.update.in          := robUpdate.in

  private val rawTaken           = Wire(Vec(p(IssueWidth), Bool()))
  private val killedByOlderTaken = Wire(Vec(p(IssueWidth), Bool()))
  private val branchMask         = Wire(Vec(p(IssueWidth), Bool()))

  killedByOlderTaken(0) := false.B

  for (w <- 0 until p(IssueWidth)) {
    rawTaken(w) := btb.queryResp.out.hit(w) && predictor.queryResp.out.taken(w)

    if (w > 0) {
      killedByOlderTaken(w) := killedByOlderTaken(w - 1) || rawTaken(w - 1)
    }
  }

  for (w <- 0 until p(IssueWidth)) {
    ifuResp.out.taken(w) := rawTaken(w) && !killedByOlderTaken(w)

    ifuResp.out.target(w) := Mux(
      ifuResp.out.taken(w),
      btb.queryResp.out.entry_out(w).target,
      ifuReq.in.query_pc(w) + p(PCStep).U
    )

    branchMask(w)               := btb.queryResp.out.hit(w) && !killedByOlderTaken(w)
    ifuResp.out.pht_index(w)    := predictor.queryResp.out.pht_index(w)
    ifuResp.out.ghr_snapshot(w) := predictor.queryResp.out.ghr_snapshot(w)
  }

  predictor.queryReq.in.is_branch := branchMask
}
