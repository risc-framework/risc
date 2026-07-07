package arch.core.cpu

import arch.configs._
import arch.core.bpu.Bpu
import arch.core.caches.{ L1DCache, L1ICache, MmioBridge }
import arch.core.csr.InterruptLines
import arch.core.decode.Decode
import arch.core.dispatch.Dispatch
import arch.core.exception.Exception
import arch.core.flush.Flush
import arch.core.fupool.{ FuPool, FunctionalUnitType }
import arch.core.ifu.Ifu
import arch.core.ibuffer.IBuffer
import arch.core.memarb.MemoryArbiter
import arch.core.regfile.Regfile
import arch.core.rob.Rob
import arch.core.sb.StoreBuffer
import arch.core.scheduler.Scheduler
import vutils.graph.Node
import chisel3._
import chisel3.util.PopCount

class Cpu(implicit p: Parameters) extends Node[Parameters]("cpu") {
  val imemReq  = outD[CpuImemReq]
  val imemResp = inD[CpuImemResp]

  val dmemReq  = outD[CpuDmemReq]
  val dmemResp = inD[CpuDmemResp]

  val mmioReq  = outD[CpuDmemReq]
  val mmioResp = inD[CpuDmemResp]

  val irq   = in[InterruptLines]
  val debug = out[CpuDebugInfo]

  private val bpu           = subnode(new Bpu)
  private val ifu           = subnode(new Ifu)
  private val ibuffer       = subnode(new IBuffer)
  private val decode        = subnode(new Decode)
  private val regfile       = subnode(new Regfile)
  private val scheduler     = subnode(new Scheduler)
  private val fuPool        = subnode(new FuPool)
  private val rob           = subnode(new Rob)
  private val exception     = subnode(new Exception)
  private val flush         = subnode(new Flush)
  private val storeBuffer   = subnode(new StoreBuffer)
  private val dispatch      = subnode(new Dispatch)
  private val memoryArbiter = subnode(new MemoryArbiter)
  private val l1ICache      = subnode(new L1ICache)
  private val l1DCache      = subnode(new L1DCache)
  private val mmioBridge    = subnode(new MmioBridge)

  private val cycleCount   = RegInit(0.U(64.W))
  private val instretCount = RegInit(0.U(64.W))

  cycleCount   := cycleCount + 1.U
  instretCount := instretCount + rob.debug.out.commit_count

  fuPool.cpu.in.cycle   := cycleCount
  fuPool.cpu.in.instret := instretCount

  link(
    l1ICache.lowerReq          -> imemReq,
    imemResp                   -> l1ICache.lowerResp,
    l1DCache.lowerReq          -> dmemReq,
    dmemResp                   -> l1DCache.lowerResp,
    ifu.icacheReq              -> l1ICache.upperReq,
    l1ICache.upperResp         -> ifu.icacheResp,
    memoryArbiter.dcacheReq    -> l1DCache.upperReq,
    l1DCache.upperResp         -> memoryArbiter.dcacheResp,
    memoryArbiter.mmioReq      -> mmioBridge.upperReq,
    mmioBridge.upperResp       -> memoryArbiter.mmioResp,
    mmioBridge.lowerReq        -> mmioReq,
    mmioResp                   -> mmioBridge.lowerResp,
    ifu.bpuReq                 -> bpu.ifuReq,
    bpu.ifuResp                -> ifu.bpuResp,
    rob.bpuUpdate              -> bpu.robUpdate,
    ifu.issued                 -> ibuffer.enq,
    ibuffer.status             -> ifu.ibufferStatus,
    ibuffer.deq                -> decode.issued,
    decode.decoded             -> dispatch.decoded,
    dispatch.dispatched        -> scheduler.dispatched,
    dispatch.rs1Read           -> regfile.rs1Read,
    dispatch.rs2Read           -> regfile.rs2Read,
    regfile.rs1Data            -> dispatch.rs1Data,
    regfile.rs2Data            -> dispatch.rs2Data,
    rob.rdWrite                -> regfile.rdWrite,
    dispatch.robReq            -> rob.dispatchReq,
    rob.dispatchResp           -> dispatch.robResp,
    scheduler.fuReq            -> fuPool.schedulerReq,
    fuPool.schedulerDone       -> scheduler.fuDone,
    fuPool.robDone             -> rob.fuDone,
    fuPool.bruResolved         -> rob.bruResolved,
    fuPool.loadMemReq          -> memoryArbiter.loadMemReq,
    memoryArbiter.loadMemResp  -> fuPool.loadMemResp,
    fuPool.loadMmioReq         -> memoryArbiter.loadMmioReq,
    memoryArbiter.loadMmioResp -> fuPool.loadMmioResp,
    fuPool.storeForwardReq     -> storeBuffer.fwdReq,
    storeBuffer.fwdResp        -> fuPool.storeForwardResp,
    storeBuffer.status         -> fuPool.storeBufferStatus,
    fuPool.storeWrite          -> storeBuffer.storeWrite,
    storeBuffer.allocStatus    -> rob.sbAllocStatus,
    rob.sbAlloc                -> storeBuffer.robAlloc,
    rob.sbCommit               -> storeBuffer.robCommit,
    storeBuffer.memReq         -> memoryArbiter.sbMemReq,
    memoryArbiter.sbMemResp    -> storeBuffer.memResp,
    storeBuffer.mmioReq        -> memoryArbiter.sbMmioReq,
    memoryArbiter.sbMmioResp   -> storeBuffer.mmioResp,
    rob.committedRedirect      -> exception.committedRedirect,
    rob.committedSync          -> exception.committedSync,
    fuPool.async               -> exception.async,
    fuPool.csrBusy             -> exception.csrBusy,
    exception.sync             -> flush.sync,
    exception.redirect         -> flush.redirect,
    exception.redirect         -> ifu.redirect,
    exception.trapUpdate       -> flush.trapUpdate,
    exception.trapUpdate       -> fuPool.trapUpdate,
    flush.globalFlush          -> ibuffer.flush,
    flush.globalFlush          -> dispatch.flush,
    flush.globalFlush          -> scheduler.flush,
    flush.globalFlush          -> fuPool.flush,
    flush.globalFlush          -> storeBuffer.flush,
    flush.globalFlush          -> rob.flush,
    irq                        -> fuPool.irq,
  )

  debug.out.cycle_count   := cycleCount
  debug.out.instret_count := instretCount

  for (w <- 0 until p(CommitWidth)) {
    debug.out.instret(w)  := rob.debug.out.instret(w)
    debug.out.pc(w)       := rob.debug.out.pc(w)
    debug.out.instr(w)    := rob.debug.out.instr(w)
    debug.out.reg_we(w)   := rob.debug.out.reg_we(w)
    debug.out.reg_addr(w) := rob.debug.out.reg_addr(w)
    debug.out.reg_data(w) := rob.debug.out.reg_data(w)
  }

  debug.out.branch_taken  := rob.bpuUpdate.out.valid && rob.bpuUpdate.out.taken
  debug.out.branch_source := Mux(rob.bpuUpdate.out.valid, rob.bpuUpdate.out.pc, 0.U)
  debug.out.branch_target := Mux(
    rob.bpuUpdate.out.valid && rob.bpuUpdate.out.taken,
    rob.bpuUpdate.out.target,
    0.U
  )

  debug.out.l1_icache_access := l1ICache.upperResp.out.fire
  debug.out.l1_icache_miss   := l1ICache.upperResp.out.fire && !l1ICache.upperResp.out.bits.hit
  debug.out.l1_dcache_access := l1DCache.upperResp.out.fire
  debug.out.l1_dcache_miss   := l1DCache.upperResp.out.fire && !l1DCache.upperResp.out.bits.hit

  debug.out.flush_cycle    := flush.globalFlush.out
  debug.out.bpu_mispredict := rob.debug.out.bpu_mispredict
  debug.out.branch_commit  := rob.debug.out.branch_commit
  debug.out.rob_empty      := rob.debug.out.empty
  debug.out.issue_count    := PopCount(
    Seq.tabulate(p(IssueWidth))(w => dispatch.dispatched.out.lanes(w).fire)
  )
  debug.out.commit_count   := rob.debug.out.commit_count

  debug.out.stall_if_redirect  := exception.redirect.out.valid
  debug.out.stall_if_ras_wait  := bpu.debug.out.ras_wait
  debug.out.stall_ibuffer_full := ibuffer.status.out.full

  debug.out.stall_decode_not_ready := Seq
    .tabulate(p(IssueWidth))(w => ibuffer.deq.out.lanes(w).valid && !ibuffer.deq.out.lanes(w).ready)
    .reduce(_ || _)

  debug.out.stall_dispatch_not_ready := Seq
    .tabulate(p(IssueWidth))(w =>
      decode.decoded.out.lanes(w).valid && !decode.decoded.out.lanes(w).ready
    )
    .reduce(_ || _)

  debug.out.stall_rob_full := Seq
    .tabulate(p(IssueWidth))(w =>
      decode.decoded.out.lanes(w).valid && decode.decoded.out.lanes(w).bits.legal && !dispatch.robReq.out
        .lanes(w)
        .ready
    )
    .reduce(_ || _)

  debug.out.stall_issue_queue_full := Seq
    .tabulate(p(IssueWidth))(w =>
      dispatch.dispatched.out.lanes(w).valid && !dispatch.dispatched.out.lanes(w).ready
    )
    .reduce(_ || _)

  debug.out.stall_lsq_full := Seq
    .tabulate(p(IssueWidth))(w =>
      decode.decoded.out.lanes(w).valid &&
        decode.decoded.out.lanes(w).bits.isStore &&
        !dispatch.robReq.out.lanes(w).ready
    )
    .reduce(_ || _)

  debug.out.stall_flush_recovery := flush.globalFlush.out

  debug.out.sched_raw_wait         := scheduler.debug.out.raw_wait
  debug.out.sched_waw_wait         := scheduler.debug.out.waw_wait
  debug.out.sched_fu_busy          := scheduler.debug.out.fu_busy
  debug.out.sched_older_lane_block := scheduler.debug.out.older_lane_block
  debug.out.sched_no_matching_fu   := scheduler.debug.out.no_matching_fu

  debug.out.frontend_stall := debug.out.stall_if_redirect ||
    debug.out.stall_if_ras_wait ||
    debug.out.stall_ibuffer_full ||
    debug.out.stall_decode_not_ready ||
    debug.out.stall_dispatch_not_ready ||
    debug.out.stall_rob_full ||
    debug.out.stall_issue_queue_full ||
    debug.out.stall_lsq_full ||
    debug.out.stall_flush_recovery

  debug.out.backend_stall := !rob.debug.out.empty && rob.debug.out.commit_count === 0.U

  private def headFuIs(fuType: FunctionalUnitType): Bool =
    rob.debug.out.head_fu_type === fuType.index.U(p(FuTypeWidth).W)

  private val backendStall = debug.out.backend_stall
  private val headWait     = backendStall && rob.debug.out.head_not_ready
  private val dcacheWait   = backendStall && fuPool.debug.out.load_wait_mem
  private val loadWait =
    headWait && headFuIs(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD)
  private val storeWait =
    backendStall &&
      ((headFuIs(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST) && rob.debug.out.head_not_ready) ||
        storeBuffer.debug.out.wait_drain)

  debug.out.mul_wait := headWait &&
    headFuIs(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_MULT)
  debug.out.div_wait := headWait &&
    headFuIs(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_DIV)
  debug.out.load_use_wait      := loadWait && !dcacheWait
  debug.out.lsu_busy           := backendStall && (fuPool.debug.out.lsu_busy || storeBuffer.debug.out.busy)
  debug.out.dcache_wait        := dcacheWait
  debug.out.store_wait         := storeWait
  debug.out.wb_conflict        := false.B
  debug.out.rob_head_not_ready := headWait
}
