package arch.core.ifu

import arch.configs._
import arch.core.bpu.BpuFetchIO
import vutils.graph.{ Node, NodeType }
import chisel3._
import chisel3.util.{ PriorityEncoder, Queue, log2Ceil }

class IfuIO(implicit p: Parameters) extends Bundle {
  val mem       = new IfuMemIO
  val exception = new IfuExceptionIO
  val bpu       = Flipped(new BpuFetchIO)
  val dispatch  = new IfuDispatchIO
}

class Ifu(implicit p: Parameters) extends Node(new IfuIO) {
  override def nodeType: NodeType  = IfuMeta.Type
  override def desiredName: String = "ifu"

  private val ibuffer = Module(new IBuffer)
  private val pc      = RegInit(p(ResetVector).U(p(XLen).W))

  private val doRedirect = io.exception.redirect

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

  private val reqFire       = io.mem.mem.req.valid && io.mem.mem.req.ready
  private val respFire      = io.mem.mem.resp.valid && io.mem.mem.resp.ready
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
    if (w > 0)
      reqKilled(w) := reqKilled(w - 1) || (reqLive(w - 1) && io.bpu.taken(w - 1))

    reqLive(w) := w.U >= reqIdx && !reqKilled(w)
  }

  private val reqTakenCands  = VecInit(
    (0 until p(IssueWidth)).map(w => reqLive(w) && io.bpu.taken(w))
  )
  private val reqHasTaken    = reqTakenCands.asUInt.orR
  private val reqTakenSlot   = PriorityEncoder(reqTakenCands.asUInt)
  private val reqTakenTarget = Mux(reqHasTaken, io.bpu.target(reqTakenSlot), nextBlockPc)

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
    io.bpu.query_pc(w) := alignedPc + (w * p(PCStep)).U

  io.bpu.advance_valid := reqFire
  io.bpu.flush         := doRedirect

  metaQ.io.flush.get := doRedirect

  io.mem.mem.req.valid       := metaQ.io.enq.ready && ibuffer.io.enq_ready && !doRedirect
  io.mem.mem.req.bits.addr   := alignedPc
  io.mem.mem.req.bits.source := 0.U
  io.mem.mem.resp.ready      := ibuffer.io.enq_ready

  io.dispatch.fetch_pc   := pc
  io.dispatch.fetch_fire := reqFire

  metaQ.io.enq.valid                 := reqFire
  metaQ.io.enq.bits.pc               := pc
  metaQ.io.enq.bits.bpu_pred_taken   := io.bpu.taken
  metaQ.io.enq.bits.bpu_pred_target  := io.bpu.target
  metaQ.io.enq.bits.bpu_pht_index    := io.bpu.pht_index
  metaQ.io.enq.bits.bpu_ghr_snapshot := io.bpu.ghr_snapshot

  when(doRedirect) {
    pc := io.exception.target
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
    if (w > 0)
      respKilled(w) := respKilled(w - 1) || (respLive(w - 1) && metaQ.io.deq.bits
        .bpu_pred_taken(w - 1))

    respLive(w) := w.U >= respIdx && !respKilled(w)
  }

  for (w <- 0 until p(IssueWidth)) {
    ibuffer.io.enq_valid(w)                 := respFire && isValidResp && metaQ.io.deq.valid && respLive(w)
    ibuffer.io.enq_bits(w).pc               := (respPc & alignMask) + (w * p(PCStep)).U
    ibuffer.io.enq_bits(w).instr            := io.mem.mem.resp.bits.data(w)
    ibuffer.io.enq_bits(w).bpu_pred_taken   := metaQ.io.deq.bits.bpu_pred_taken(w)
    ibuffer.io.enq_bits(w).bpu_pred_target  := metaQ.io.deq.bits.bpu_pred_target(w)
    ibuffer.io.enq_bits(w).bpu_pht_index    := metaQ.io.deq.bits.bpu_pht_index(w)
    ibuffer.io.enq_bits(w).bpu_ghr_snapshot := metaQ.io.deq.bits.bpu_ghr_snapshot(w)
  }

  ibuffer.io.flush := doRedirect

  for (w <- 0 until p(IssueWidth)) {
    ibuffer.io.deq(w).ready  := io.dispatch.out(w).ready
    io.dispatch.out(w).valid := ibuffer.io.deq(w).valid && !doRedirect
    io.dispatch.out(w).bits  := ibuffer.io.deq(w).bits
  }

  io.dispatch.frontend_flush := doRedirect
  io.dispatch.reset_ibuffer  := doRedirect
}
