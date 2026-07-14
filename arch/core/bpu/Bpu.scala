package arch.core.bpu

import arch.configs._
import vutils.graph.Node
import chisel3._
import chisel3.util.PriorityEncoder

class Bpu(implicit p: Parameters) extends Node[Parameters]("bpu") {
  val ifuReq    = in[BpuIfuReq]
  val ifuResp   = out[BpuIfuResp]
  val robUpdate = in[BpuUpdate]
  val debug     = out[BpuDebugInfo]

  private val btb       = subnode(new Btb)
  private val predictor = subnode(new Predictor(p(BpuPredictorKind)))
  private val ras       = subnode(new Ras)

  btb.queryReq.in.pc := ifuReq.in.query_pc
  btb.update.in      := robUpdate.in

  private val predictorUpdate = WireDefault(robUpdate.in)
  predictorUpdate.valid := robUpdate.in.valid && robUpdate.in.branch_kind === BpuBranchKind.BRANCH

  predictor.queryReq.in.pc     := ifuReq.in.query_pc
  predictor.queryReq.in.accept := ifuReq.in.advance_valid && !ifuReq.in.flush
  predictor.queryReq.in.flush  := ifuReq.in.flush
  predictor.update.in          := predictorUpdate
  ras.req.in.flush             := ifuReq.in.flush
  ras.req.in.update            := robUpdate.in

  private val rawTaken           = Wire(Vec(p(IssueWidth), Bool()))
  private val killedByOlderTaken = Wire(Vec(p(IssueWidth), Bool()))
  private val branchMask         = Wire(Vec(p(IssueWidth), Bool()))
  private val selectedKindVec    = Wire(Vec(p(IssueWidth), UInt(BpuBranchKind.width.W)))
  private val selectedPushVec    = Wire(Vec(p(IssueWidth), UInt(p(XLen).W)))

  killedByOlderTaken(0) := false.B

  for (w <- 0 until p(IssueWidth)) {
    val kind = btb.queryResp.out.entry_out(w).kind

    rawTaken(w) := btb.queryResp.out.hit(w) &&
      (BpuBranchKind.isUnconditional(kind) || predictor.queryResp.out.taken(w))

    if (w > 0) {
      killedByOlderTaken(w) := killedByOlderTaken(w - 1) || rawTaken(w - 1)
    }
  }

  for (w <- 0 until p(IssueWidth)) {
    val kind      = btb.queryResp.out.entry_out(w).kind
    val rasTarget =
      (kind === BpuBranchKind.RET || kind === BpuBranchKind.CALL_RET) && ras.resp.out.valid

    ifuResp.out.btb_hit(w) := btb.queryResp.out.hit(w) && !killedByOlderTaken(w)
    ifuResp.out.taken(w)   := rawTaken(w) && !killedByOlderTaken(w)

    ifuResp.out.target(w) := Mux(
      ifuResp.out.taken(w),
      Mux(rasTarget, ras.resp.out.target, btb.queryResp.out.entry_out(w).target),
      ifuReq.in.query_pc(w) + p(PCStep).U
    )

    branchMask(w)               := btb.queryResp.out.hit(w) &&
      kind === BpuBranchKind.BRANCH &&
      !killedByOlderTaken(w)
    ifuResp.out.pht_index(w)    := predictor.queryResp.out.pht_index(w)
    ifuResp.out.ghr_snapshot(w) := predictor.queryResp.out.ghr_snapshot(w)
    ifuResp.out.provider(w)     := predictor.queryResp.out.provider(w)
    ifuResp.out.alt_taken(w)    := predictor.queryResp.out.alt_taken(w)
    selectedKindVec(w)          := Mux(ifuResp.out.taken(w), kind, BpuBranchKind.NONE)
    selectedPushVec(w)          := ifuReq.in.query_pc(w) + p(PCStep).U
  }

  predictor.queryReq.in.is_branch := branchMask

  private val selectedValidVec = VecInit(
    (0 until p(IssueWidth)).map(w =>
      ifuResp.out.taken(w) &&
        (selectedKindVec(w) === BpuBranchKind.CALL ||
          selectedKindVec(w) === BpuBranchKind.RET ||
          selectedKindVec(w) === BpuBranchKind.CALL_RET)
    )
  )
  private val selectedAny  = selectedValidVec.asUInt.orR
  private val selectedSlot = PriorityEncoder(selectedValidVec.asUInt)
  private val selectedIsRet =
    selectedKindVec(selectedSlot) === BpuBranchKind.RET ||
      selectedKindVec(selectedSlot) === BpuBranchKind.CALL_RET

  ras.req.in.accept      := ifuReq.in.advance_valid && !ifuReq.in.flush && selectedAny
  ras.req.in.predictKind := Mux(selectedAny, selectedKindVec(selectedSlot), BpuBranchKind.NONE)
  ras.req.in.pushAddr    := Mux(selectedAny, selectedPushVec(selectedSlot), 0.U)

  debug.out.ras_wait := ifuReq.in.advance_valid && selectedAny && selectedIsRet && !ras.resp.out.valid
}
