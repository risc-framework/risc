package arch.core.ifu

import arch.configs._
import arch.core.bpu.{ BpuIfuReq, BpuIfuResp }
import arch.core.ibuffer.{ IBufferEntry, IBufferStatus }
import vcache.{ CacheReadReq, CacheResp }
import vutils.graph.Node
import chisel3._
import chisel3.util.{ PriorityEncoder, Queue, log2Ceil }

class Ifu(implicit p: Parameters) extends Node[Parameters]("ifu") {
  val icacheReq = outDWith[CacheReadReq] { p =>
    new CacheReadReq(p(L1ICacheParams))
  }

  val icacheResp = inDWith[CacheResp[Vec[UInt]]] { p =>
    new CacheResp(Vec(p(IssueWidth), UInt(p(ILen).W)), p(L1ICacheParams))
  }

  val bpuReq  = out[BpuIfuReq]
  val bpuResp = in[BpuIfuResp]

  val ibufferStatus = in[IBufferStatus]
  val redirect      = in[RedirectInfo]
  val issued        = outDVec[IBufferEntry](p => p(IssueWidth))

  private val pc = RegInit(p(ResetVector).U(p(XLen).W))

  class FetchMeta extends Bundle {
    val pc               = UInt(p(XLen).W)
    val bpu_btb_hit      = Vec(p(IssueWidth), Bool())
    val bpu_pred_taken   = Vec(p(IssueWidth), Bool())
    val bpu_pred_target  = Vec(p(IssueWidth), UInt(p(XLen).W))
    val bpu_pht_index    = Vec(p(IssueWidth), UInt(p(GShareGhrWidth).W))
    val bpu_ghr_snapshot = Vec(p(IssueWidth), UInt(p(GShareGhrWidth).W))
  }

  private val metaQ = Module(new Queue(new FetchMeta, 8, hasFlush = true))

  private val dropCount   = RegInit(0.U(5.W))
  private val pendingReqs = RegInit(0.U(5.W))

  private val reqFire       = icacheReq.out.valid && icacheReq.out.ready
  private val respFire      = icacheResp.in.valid && icacheResp.in.ready
  private val nextDropCount = dropCount + pendingReqs
  private val isDropping    = respFire && nextDropCount > 0.U
  private val isValidResp   = dropCount === 0.U && !redirect.in.valid

  private val alignBytes  = p(IssueWidth) * p(BytesPerInstr)
  private val alignMask   = ~(alignBytes - 1).U(p(XLen).W)
  private val alignedPc   = pc & alignMask
  private val nextBlockPc = alignedPc + alignBytes.U

  private val reqIdx =
    if (p(IssueWidth) > 1) pc(log2Ceil(alignBytes) - 1, log2Ceil(p(BytesPerInstr))) else 0.U

  private val reqKilled = Wire(Vec(p(IssueWidth), Bool()))
  private val reqLive   = Wire(Vec(p(IssueWidth), Bool()))

  reqKilled(0) := false.B

  for (w <- 0 until p(IssueWidth)) {
    if (w > 0) {
      reqKilled(w) := reqKilled(w - 1) || (reqLive(w - 1) && bpuResp.in.taken(w - 1))
    }

    reqLive(w) := w.U >= reqIdx && !reqKilled(w)
  }

  private val reqTakenCands = VecInit(
    (0 until p(IssueWidth)).map(w => reqLive(w) && bpuResp.in.taken(w))
  )

  private val reqHasTaken    = reqTakenCands.asUInt.orR
  private val reqTakenSlot   = PriorityEncoder(reqTakenCands.asUInt)
  private val reqTakenTarget = Mux(reqHasTaken, bpuResp.in.target(reqTakenSlot), nextBlockPc)

  when(redirect.in.valid) {
    dropCount   := nextDropCount - Mux(isDropping, 1.U, 0.U)
    pendingReqs := 0.U
  }.otherwise {
    val isValidRespFire = respFire && dropCount === 0.U
    val isDropRespFire  = respFire && dropCount > 0.U

    when(reqFire && !isValidRespFire) {
      pendingReqs := pendingReqs + 1.U
    }.elsewhen(!reqFire && isValidRespFire) {
      pendingReqs := pendingReqs - 1.U
    }

    when(isDropRespFire) {
      dropCount := dropCount - 1.U
    }
  }

  for (w <- 0 until p(IssueWidth))
    bpuReq.out.query_pc(w) := alignedPc + (w * p(PCStep)).U

  bpuReq.out.advance_valid := reqFire
  bpuReq.out.flush         := redirect.in.valid

  metaQ.io.flush.get := redirect.in.valid

  icacheReq.out.valid     := metaQ.io.enq.ready && ibufferStatus.in.ready && !redirect.in.valid
  icacheReq.out.bits.addr := alignedPc

  icacheResp.in.ready := ibufferStatus.in.ready

  metaQ.io.enq.valid                 := reqFire
  metaQ.io.enq.bits.pc               := pc
  metaQ.io.enq.bits.bpu_btb_hit      := bpuResp.in.btb_hit
  metaQ.io.enq.bits.bpu_pred_taken   := bpuResp.in.taken
  metaQ.io.enq.bits.bpu_pred_target  := bpuResp.in.target
  metaQ.io.enq.bits.bpu_pht_index    := bpuResp.in.pht_index
  metaQ.io.enq.bits.bpu_ghr_snapshot := bpuResp.in.ghr_snapshot

  when(redirect.in.valid) {
    pc := redirect.in.target
  }.elsewhen(reqFire) {
    pc := reqTakenTarget
  }

  metaQ.io.deq.ready := respFire && isValidResp

  private val respPc     = metaQ.io.deq.bits.pc
  private val respIdx    =
    if (p(IssueWidth) > 1) respPc(log2Ceil(alignBytes) - 1, log2Ceil(p(BytesPerInstr))) else 0.U
  private val respKilled = Wire(Vec(p(IssueWidth), Bool()))
  private val respLive   = Wire(Vec(p(IssueWidth), Bool()))

  respKilled(0) := false.B

  for (w <- 0 until p(IssueWidth)) {
    if (w > 0) {
      respKilled(w) := respKilled(w - 1) || (respLive(w - 1) && metaQ.io.deq.bits
        .bpu_pred_taken(w - 1))
    }

    respLive(w) := w.U >= respIdx && !respKilled(w)
  }

  for (w <- 0 until p(IssueWidth)) {
    issued.out.lanes(w).valid                 := respFire && isValidResp && metaQ.io.deq.valid && respLive(w)
    issued.out.lanes(w).bits.pc               := (respPc & alignMask) + (w * p(PCStep)).U
    issued.out.lanes(w).bits.instr            := icacheResp.in.bits.data(w)
    issued.out.lanes(w).bits.bpu_btb_hit      := metaQ.io.deq.bits.bpu_btb_hit(w)
    issued.out.lanes(w).bits.bpu_pred_taken   := metaQ.io.deq.bits.bpu_pred_taken(w)
    issued.out.lanes(w).bits.bpu_pred_target  := metaQ.io.deq.bits.bpu_pred_target(w)
    issued.out.lanes(w).bits.bpu_pht_index    := metaQ.io.deq.bits.bpu_pht_index(w)
    issued.out.lanes(w).bits.bpu_ghr_snapshot := metaQ.io.deq.bits.bpu_ghr_snapshot(w)
  }
}
