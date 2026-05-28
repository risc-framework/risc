package arch.core.ifu

import arch.configs._
import vcache.CacheReadOnlyPortIO
import chisel3._
import chisel3.util.{ log2Ceil, Queue, PriorityEncoder }

class Ifu(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_ifu"

  val mem = IO(new CacheReadOnlyPortIO(Vec(p(IssueWidth), UInt(p(ILen).W)), p(L1ICacheParams)))

  val bru_taken  = IO(Input(Bool()))
  val bru_target = IO(Input(UInt(p(XLen).W)))

  val bpu_taken_in        = IO(Input(Vec(p(IssueWidth), Bool())))
  val bpu_target_in       = IO(Input(Vec(p(IssueWidth), UInt(p(XLen).W))))
  val bpu_pht_index_in    = IO(Input(Vec(p(IssueWidth), UInt(p(GShareGhrWidth).W))))
  val bpu_ghr_snapshot_in = IO(Input(Vec(p(IssueWidth), UInt(p(GShareGhrWidth).W))))

  val bru_not_taken = IO(Input(Bool()))
  val bru_branch_pc = IO(Input(UInt(p(XLen).W)))

  val take_trap   = IO(Input(Bool()))
  val trap_target = IO(Input(UInt(p(XLen).W)))

  val fetch_pc      = IO(Output(UInt(p(XLen).W)))
  val fronend_flush = IO(Output(Bool()))

  val if_valid            = IO(Output(Vec(p(IssueWidth), Bool())))
  val if_instr            = IO(Output(Vec(p(IssueWidth), UInt(p(ILen).W))))
  val if_pc               = IO(Output(Vec(p(IssueWidth), UInt(p(XLen).W))))
  val if_bpu_pred_taken   = IO(Output(Vec(p(IssueWidth), Bool())))
  val if_bpu_pred_target  = IO(Output(Vec(p(IssueWidth), UInt(p(XLen).W))))
  val if_bpu_pht_index    = IO(Output(Vec(p(IssueWidth), UInt(p(GShareGhrWidth).W))))
  val if_bpu_ghr_snapshot = IO(Output(Vec(p(IssueWidth), UInt(p(GShareGhrWidth).W))))
  val fetch_fire          = IO(Output(Bool()))

  val dispatch_fire = IO(Input(Vec(p(IssueWidth), Bool())))
  val reset_ibuffer = IO(Output(Bool()))

  val ibuffer     = Module(new IBuffer)
  val pc          = RegInit(p(ResetVector).U(p(XLen).W))
  val do_redirect = take_trap || bru_taken || bru_not_taken

  class FetchMeta extends Bundle {
    val pc               = UInt(p(XLen).W)
    val bpu_pred_taken   = Vec(p(IssueWidth), Bool())
    val bpu_pred_target  = Vec(p(IssueWidth), UInt(p(XLen).W))
    val bpu_pht_index    = Vec(p(IssueWidth), UInt(p(GShareGhrWidth).W))
    val bpu_ghr_snapshot = Vec(p(IssueWidth), UInt(p(GShareGhrWidth).W))
  }

  val meta_q = Module(new Queue(new FetchMeta, 8, hasFlush = true))

  val drop_count   = RegInit(0.U(5.W))
  val pending_reqs = RegInit(0.U(5.W))

  val req_fire        = mem.req.valid && mem.req.ready
  val resp_fire       = mem.resp.valid && mem.resp.ready
  val next_drop_count = drop_count + pending_reqs
  val is_dropping     = resp_fire && next_drop_count > 0.U

  when(do_redirect) {
    drop_count   := next_drop_count - Mux(is_dropping, 1.U, 0.U)
    pending_reqs := 0.U
  }.otherwise {
    val is_valid_resp_fire = resp_fire && drop_count === 0.U
    val is_drop_resp_fire  = resp_fire && drop_count > 0.U
    when(req_fire && !is_valid_resp_fire) {
      pending_reqs := pending_reqs + 1.U
    }.elsewhen(!req_fire && is_valid_resp_fire) {
      pending_reqs := pending_reqs - 1.U
    }
    when(is_drop_resp_fire) {
      drop_count := drop_count - 1.U
    }
  }

  val is_valid_resp        = drop_count === 0.U && !do_redirect
  val align_bytes          = p(IssueWidth) * p(BytesPerInstr)
  val align_mask           = ~(align_bytes - 1).U(p(XLen).W)
  val aligned_pc           = pc & align_mask
  val next_block_pc        = aligned_pc + align_bytes.U
  val req_idx              =
    if (p(IssueWidth) > 1) pc(log2Ceil(align_bytes) - 1, log2Ceil(p(BytesPerInstr))) else 0.U
  val req_taken_candidates = VecInit(
    (0 until p(IssueWidth)).map(w => w.U >= req_idx && bpu_taken_in(w))
  )
  val req_has_taken        = req_taken_candidates.asUInt.orR
  val req_taken_slot       = PriorityEncoder(req_taken_candidates.asUInt)
  val req_taken_tgt        = Mux(req_has_taken, bpu_target_in(req_taken_slot), next_block_pc)

  meta_q.io.flush.get := do_redirect
  mem.req.valid       := meta_q.io.enq.ready && ibuffer.enq_ready && !do_redirect
  mem.req.bits.addr   := aligned_pc
  mem.req.bits.source := 0.U
  mem.resp.ready      := ibuffer.enq_ready

  fetch_pc   := pc
  fetch_fire := req_fire

  meta_q.io.enq.valid                 := req_fire
  meta_q.io.enq.bits.pc               := pc
  meta_q.io.enq.bits.bpu_pred_taken   := bpu_taken_in
  meta_q.io.enq.bits.bpu_pred_target  := bpu_target_in
  meta_q.io.enq.bits.bpu_pht_index    := bpu_pht_index_in
  meta_q.io.enq.bits.bpu_ghr_snapshot := bpu_ghr_snapshot_in

  when(take_trap) {
    pc := trap_target
  }.elsewhen(bru_taken) {
    pc := bru_target
  }.elsewhen(bru_not_taken) {
    pc := bru_branch_pc + p(PCStep).U
  }.elsewhen(req_fire) {
    pc := req_taken_tgt
  }

  meta_q.io.deq.ready := resp_fire && is_valid_resp

  val resp_pc               = meta_q.io.deq.bits.pc
  val resp_idx              =
    if (p(IssueWidth) > 1) resp_pc(log2Ceil(align_bytes) - 1, log2Ceil(p(BytesPerInstr))) else 0.U
  val resp_taken_candidates = VecInit(
    (0 until p(IssueWidth)).map(w => w.U >= resp_idx && meta_q.io.deq.bits.bpu_pred_taken(w))
  )
  val resp_has_taken        = resp_taken_candidates.asUInt.orR
  val resp_taken_slot       = PriorityEncoder(resp_taken_candidates.asUInt)

  for (w <- 0 until p(IssueWidth)) {
    val is_valid_pos = w.U >= resp_idx
    val before_taken = !resp_has_taken || w.U <= resp_taken_slot
    ibuffer.enq_valid(
      w
    )                                    := resp_fire && is_valid_resp && meta_q.io.deq.valid && is_valid_pos && before_taken
    ibuffer.enq_bits(w).pc               := (resp_pc & align_mask) + (w * p(PCStep)).U
    ibuffer.enq_bits(w).instr            := mem.resp.bits.data(w)
    ibuffer.enq_bits(w).bpu_pred_taken   := meta_q.io.deq.bits.bpu_pred_taken(w)
    ibuffer.enq_bits(w).bpu_pred_target  := meta_q.io.deq.bits.bpu_pred_target(w)
    ibuffer.enq_bits(w).bpu_pht_index    := meta_q.io.deq.bits.bpu_pht_index(w)
    ibuffer.enq_bits(w).bpu_ghr_snapshot := meta_q.io.deq.bits.bpu_ghr_snapshot(w)
  }

  ibuffer.flush := do_redirect

  for (w <- 0 until p(IssueWidth)) {
    ibuffer.deq(w).ready   := dispatch_fire(w)
    if_valid(w)            := ibuffer.deq(w).valid && !do_redirect
    if_instr(w)            := ibuffer.deq(w).bits.instr
    if_pc(w)               := ibuffer.deq(w).bits.pc
    if_bpu_pred_taken(w)   := ibuffer.deq(w).bits.bpu_pred_taken
    if_bpu_pred_target(w)  := ibuffer.deq(w).bits.bpu_pred_target
    if_bpu_pht_index(w)    := ibuffer.deq(w).bits.bpu_pht_index
    if_bpu_ghr_snapshot(w) := ibuffer.deq(w).bits.bpu_ghr_snapshot
  }

  fronend_flush := do_redirect
  reset_ibuffer := do_redirect
}
