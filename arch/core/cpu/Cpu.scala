package arch.core.cpu

import arch.configs._
import arch.core.bpu.Bpu
import arch.core.caches.{ L1DCache, L1ICache, MmioBridge }
import arch.core.csr.InterruptLines
import arch.core.decode.Decode
import arch.core.dispatch.Dispatch
import arch.core.exception.Exception
import arch.core.flush.Flush
import arch.core.fupool.FuPool
import arch.core.ifu.Ifu
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

  require(p(NumLDs) > 0, "Cpu: at least one LD node is required")
  require(p(NumSTs) > 0, "Cpu: at least one ST node is required")
  require(p(NumBRUs) > 0, "Cpu: at least one BRU node is required")
  require(p(NumCSRs) <= 1, "Cpu: at most one CSR node is supported")

  private val bpu           = subnode(new Bpu)
  private val ifu           = subnode(new Ifu)
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
  fuPool.cpu.in.irq     := irq.in

  link(
    imemReq                    -> l1ICache.lowerReq,
    l1ICache.lowerResp         -> imemResp,
    dmemReq                    -> l1DCache.lowerReq,
    l1DCache.lowerResp         -> dmemResp,
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
    ifu.decode                 -> decode.ifu,
    decode.dispatch            -> dispatch.decode,
    dispatch.schedulerReq      -> scheduler.dispatchReq,
    dispatch.regfileReq        -> regfile.dispatchReq,
    regfile.dispatchResp       -> dispatch.regfileResp,
    dispatch.robReq            -> rob.dispatchReq,
    rob.dispatchResp           -> dispatch.robResp,
    dispatch.sbReq             -> storeBuffer.dispatchReq,
    storeBuffer.dispatchResp   -> dispatch.sbResp,
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
    rob.regfileWrite           -> regfile.robWrite,
    rob.sbCommit               -> storeBuffer.robCommit,
    storeBuffer.memReq         -> memoryArbiter.sbMemReq,
    memoryArbiter.sbMemResp    -> storeBuffer.memResp,
    storeBuffer.mmioReq        -> memoryArbiter.sbMmioReq,
    memoryArbiter.sbMmioResp   -> storeBuffer.mmioResp,
    rob.flush                  -> flush.rob,
    flush.redirect             -> exception.redirectReq,
    flush.sync                 -> exception.syncReq,
    fuPool.asyncException      -> exception.asyncReq,
    fuPool.exceptionStatus     -> exception.csrStatus,
    exception.flush            -> flush.exception,
    flush.ifuReq               -> ifu.exceptionReq,
    ifu.exceptionResp          -> exception.ifuResp,
    flush.dispatchReq          -> dispatch.exception,
    flush.schedulerReq         -> scheduler.exception,
    flush.storeBufferReq       -> storeBuffer.exception,
    flush.fuPoolReq            -> fuPool.exceptionReq,
    flush.robReq               -> rob.exceptionReq,
    rob.exceptionResp          -> exception.robResp
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

  debug.out.l1_icache_access := l1ICache.upperResp.out.valid && l1ICache.upperResp.out.ready
  debug.out.l1_icache_miss   := l1ICache.upperResp.out.valid && l1ICache.upperResp.out.ready && !l1ICache.upperResp.out.bits.hit
  debug.out.l1_dcache_access := l1DCache.upperResp.out.valid && l1DCache.upperResp.out.ready
  debug.out.l1_dcache_miss   := l1DCache.upperResp.out.valid && l1DCache.upperResp.out.ready && !l1DCache.upperResp.out.bits.hit

  debug.out.flush_cycle    := exception.debug.out.flush_valid
  debug.out.bpu_mispredict := rob.debug.out.bpu_mispredict
  debug.out.branch_commit  := rob.debug.out.branch_commit
  debug.out.rob_empty      := rob.debug.out.empty
  debug.out.issue_count    := PopCount(
    Seq.tabulate(p(IssueWidth))(w => dispatch.schedulerReq.out.lanes(w).fire)
  )
  debug.out.commit_count   := rob.debug.out.commit_count

  debug.out.stall_if_redirect  := flush.ifuReq.out.redirect
  debug.out.stall_if_ras_wait  := bpu.debug.out.ras_wait
  debug.out.stall_ibuffer_full := ifu.debug.out.ibuffer_full

  debug.out.stall_decode_not_ready := Seq
    .tabulate(p(IssueWidth))(w => ifu.decode.out.lanes(w).valid && !ifu.decode.out.lanes(w).ready)
    .reduce(_ || _)

  debug.out.stall_dispatch_not_ready := Seq
    .tabulate(p(IssueWidth))(w =>
      decode.dispatch.out.lanes(w).valid && !decode.dispatch.out.lanes(w).ready
    )
    .reduce(_ || _)

  debug.out.stall_rob_full := Seq
    .tabulate(p(IssueWidth))(w =>
      decode.dispatch.out.lanes(w).valid && !dispatch.robReq.out.lanes(w).ready
    )
    .reduce(_ || _)

  debug.out.stall_issue_queue_full := Seq
    .tabulate(p(IssueWidth))(w =>
      dispatch.schedulerReq.out.lanes(w).valid && !dispatch.schedulerReq.out.lanes(w).ready
    )
    .reduce(_ || _)

  debug.out.stall_lsq_full := Seq
    .tabulate(p(IssueWidth))(w =>
      dispatch.sbReq.out.lanes(w).valid &&
        dispatch.sbReq.out.lanes(w).bits.isStore &&
        !storeBuffer.dispatchResp.out.lanes(w).ready
    )
    .reduce(_ || _)

  debug.out.stall_flush_recovery := exception.debug.out.flush_valid

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
}
