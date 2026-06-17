package arch.core.ifu

import arch.configs._
import arch.core.bpu.{ BpuIfuReq, BpuIfuResp }
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

  val decode = outDVecWith[IBufferEntry](p => p(IssueWidth)) { p =>
    new IBufferEntry()(p)
  }

  val exceptionReq  = in[IfuExceptionReq]
  val exceptionResp = out[IfuExceptionResp]
  val debug         = out[IfuDebugInfo]

  private val ibuffer = subnode(new IBuffer)
  private val pc      = RegInit(p(ResetVector).U(p(XLen).W))

  private val doRedirect = exceptionReq.in.redirect

  class FetchMeta extends Bundle {
    val pc               = UInt(p(XLen).W)
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
  private val isValidResp   = dropCount === 0.U && !doRedirect

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

  when(doRedirect) {
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
  bpuReq.out.flush         := doRedirect

  metaQ.io.flush.get := doRedirect

  icacheReq.out.valid       := metaQ.io.enq.ready && ibuffer.status.out.enq_ready && !doRedirect
  icacheReq.out.bits.addr   := alignedPc
  icacheReq.out.bits.source := 0.U

  icacheResp.in.ready := ibuffer.status.out.enq_ready

  exceptionResp.out.fetch_pc := pc

  metaQ.io.enq.valid                 := reqFire
  metaQ.io.enq.bits.pc               := pc
  metaQ.io.enq.bits.bpu_pred_taken   := bpuResp.in.taken
  metaQ.io.enq.bits.bpu_pred_target  := bpuResp.in.target
  metaQ.io.enq.bits.bpu_pht_index    := bpuResp.in.pht_index
  metaQ.io.enq.bits.bpu_ghr_snapshot := bpuResp.in.ghr_snapshot

  when(doRedirect) {
    pc := exceptionReq.in.target
  }.elsewhen(reqFire) {
    pc := reqTakenTarget
  }

  metaQ.io.deq.ready := respFire && isValidResp

  private val respPc =
    metaQ.io.deq.bits.pc

  private val respIdx =
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
    ibuffer.enqValid.in.lanes(w) := respFire && isValidResp && metaQ.io.deq.valid && respLive(w)

    ibuffer.enqBits.in.lanes(w).pc               := (respPc & alignMask) + (w * p(PCStep)).U
    ibuffer.enqBits.in.lanes(w).instr            := icacheResp.in.bits.data(w)
    ibuffer.enqBits.in.lanes(w).bpu_pred_taken   := metaQ.io.deq.bits.bpu_pred_taken(w)
    ibuffer.enqBits.in.lanes(w).bpu_pred_target  := metaQ.io.deq.bits.bpu_pred_target(w)
    ibuffer.enqBits.in.lanes(w).bpu_pht_index    := metaQ.io.deq.bits.bpu_pht_index(w)
    ibuffer.enqBits.in.lanes(w).bpu_ghr_snapshot := metaQ.io.deq.bits.bpu_ghr_snapshot(w)
  }

  ibuffer.flush.in.flush := doRedirect
  debug.out.ibuffer_full := ibuffer.status.out.full

  link(
    ibuffer.deq -> decode
  )
}
