package arch.core.cpu

import arch.core.bpu.Bpu
import arch.core.csr.{ CsrTrapView, InterruptLines }
import arch.core.decode.Decode
import arch.core.exception.Exception
import arch.core.fupool.FuPool
import arch.core.ifu.Ifu
import arch.core.interrupt.{ Interrupt, TrapCandidate }
import arch.core.memarb.MemoryArbiter
import arch.core.regfile.Regfile
import arch.core.rob.Rob
import arch.core.sb.StoreBuffer
import arch.core.scheduler.Scheduler
import arch.core.flush.Flush
import arch.core.dispatch.Dispatch
import arch.configs._
import vcache.CachePortIO
import vcache.nonblocking.{ NonBlockingCache, ReadOnlyNonBlockingCache }
import vutils.graph.{ Node, NodeType }
import chisel3._
import chisel3.util.{ PopCount, log2Ceil }

class CpuIO(implicit p: Parameters) extends Bundle {
  val imem  = new CachePortIO(Vec(p(IssueWidth), UInt(p(ILen).W)), p(L1ICacheParams))
  val dmem  = new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams))
  val mmio  = new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams))
  val irq   = Input(new InterruptLines)
  val debug = Output(new DebugIO)
}

class Cpu(implicit p: Parameters) extends Node(new CpuIO) {
  override def nodeType: NodeType  = CpuMeta.Type
  override def desiredName: String = "cpu"

  require(p(NumLDs) > 0, "Cpu: at least one LD node is required")
  require(p(NumSTs) > 0, "Cpu: at least one ST node is required")
  require(p(NumBRUs) > 0, "Cpu: at least one BRU node is required")
  require(p(NumCSRs) <= 1, "Cpu: at most one CSR node is supported")

  private val bpu           = Module(new Bpu)
  private val ifu           = Module(new Ifu)
  private val decode        = Module(new Decode)
  private val regfile       = Module(new Regfile)
  private val scheduler     = Module(new Scheduler)
  private val fuPool        = Module(new FuPool)
  private val rob           = Module(new Rob)
  private val interrupt     = Module(new Interrupt)
  private val exception     = Module(new Exception)
  private val storeBuffer   = Module(new StoreBuffer)
  private val dispatch      = Module(new Dispatch)
  private val flush         = Module(new Flush)
  private val memoryArbiter = Module(new MemoryArbiter)
  private val l1ICache      = Module(
    new ReadOnlyNonBlockingCache(Vec(p(IssueWidth), UInt(p(ILen).W)), p(L1ICacheParams))
  )
  private val l1DCache      = Module(new NonBlockingCache(UInt(p(XLen).W), p(L1DCacheParams)))

  scheduler.bind(fuPool)

  private val cycleCount     = RegInit(0.U(64.W))
  private val instretCount   = RegInit(0.U(64.W))
  private val commitPopCount = Wire(UInt(log2Ceil(p(IssueWidth) + 1).W))

  commitPopCount := PopCount(rob.io.commit.lanes.map(_.pop))
  cycleCount     := cycleCount + 1.U
  instretCount   := instretCount + commitPopCount

  ifu.io.mem.mem <> l1ICache.upper
  io.imem <> l1ICache.lower

  l1DCache.upper <> memoryArbiter.io.out.mem
  io.dmem <> l1DCache.lower
  io.mmio <> memoryArbiter.io.out.mmio

  ifu.io.bpu <> bpu.io.fetch

  for (i <- 0 until p(NumLDs)) {
    memoryArbiter.io.load.mem(i) <> fuPool.io.ld_mem.ports(i).mem
    memoryArbiter.io.load.mmio(i) <> fuPool.io.ld_mem.ports(i).mmio

    fuPool.io.ld_sb.ports(i).sb_fwd <> storeBuffer.io.fwd.ports(i)
    fuPool.io.ld_sb.ports(i).oldest_valid := storeBuffer.io.state.oldestValid
    fuPool.io.ld_sb.ports(i).oldest_seq   := storeBuffer.io.state.oldestSeq
  }

  for (i <- 0 until p(NumSTs))
    storeBuffer.io.write.ports(i) := fuPool.io.st_sb.ports(i).write

  memoryArbiter.io.store.mem <> storeBuffer.io.mem.mem
  memoryArbiter.io.store.mmio <> storeBuffer.io.mem.mmio

  for (i <- 0 until p(NumBRUs))
    rob.io.bru.ports(i) <> fuPool.io.bru.ports(i)

  for (i <- 0 until p(NumFUs)) {
    rob.io.wb.ports(i).valid   := fuPool.io.fu.done(i).valid
    rob.io.wb.ports(i).rob_tag := fuPool.io.fu.done(i).bits.rob_tag
    rob.io.wb.ports(i).data    := fuPool.io.fu.done(i).bits.result

    rob.io.trap.ports(i).valid             := fuPool.io.fu
      .done(i)
      .valid && (fuPool.io.fu.done(i).bits.trap_req || fuPool.io.fu.done(i).bits.trap_ret)
    rob.io.trap.ports(i).bits.rob_tag      := fuPool.io.fu.done(i).bits.rob_tag
    rob.io.trap.ports(i).bits.trap_req     := fuPool.io.fu.done(i).bits.trap_req
    rob.io.trap.ports(i).bits.trap_target  := fuPool.io.fu.done(i).bits.trap_target
    rob.io.trap.ports(i).bits.trap_ret     := fuPool.io.fu.done(i).bits.trap_ret
    rob.io.trap.ports(i).bits.trap_ret_tgt := fuPool.io.fu.done(i).bits.trap_ret_tgt
  }

  flush.io.rob <> rob.io.flush
  flush.io.exception <> exception.io.flush

  private val archPc = Mux(rob.io.ctrl.empty, ifu.io.dispatch.fetch_pc, rob.io.commit.lanes(0).pc)
  exception.io.archPc := archPc

  if (p(NumCSRs) > 0) {
    interrupt.io.view := fuPool.io.csr.ports(0).view
    interrupt.io.irq <> io.irq

    exception.io.interrupt := interrupt.io.out
    exception.io.csrBusy   := fuPool.io.csr.ports(0).busy

    fuPool.io.csr.ports(0).cycle       := cycleCount
    fuPool.io.csr.ports(0).instret     := instretCount
    fuPool.io.csr.ports(0).irq <> io.irq
    fuPool.io.csr.ports(0).arch_pc     := archPc
    fuPool.io.csr.ports(0).trap_update := exception.io.csrTrapUpdate
  } else {
    interrupt.io.view := 0.U.asTypeOf(new CsrTrapView)
    interrupt.io.irq  := 0.U.asTypeOf(new InterruptLines)

    exception.io.interrupt := 0.U.asTypeOf(new TrapCandidate)
    exception.io.csrBusy   := false.B
  }

  ifu.io.exception <> exception.io.ifu
  storeBuffer.io.exception <> exception.io.sb
  scheduler.io.exception <> exception.io.scheduler
  fuPool.io.exception <> exception.io.fu_pool
  rob.io.exception <> exception.io.rob

  decode.io.ifu <> ifu.io.decode
  decode.io.dispatch <> dispatch.io.decode
  dispatch.io.scheduler <> scheduler.io.dispatch
  dispatch.io.regfile <> regfile.io.dispatch
  dispatch.io.rob <> rob.io.dispatch
  dispatch.io.sb <> storeBuffer.io.dispatch
  dispatch.io.exception <> exception.io.dispatch

  private val commitRegWe   = Wire(Vec(p(IssueWidth), Bool()))
  private val commitRegAddr = Wire(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
  private val commitRegData = Wire(Vec(p(IssueWidth), UInt(p(XLen).W)))

  for (w <- 0 until p(IssueWidth)) {
    rob.io.commit.lanes(w).pop := rob.io.commit.lanes(w).valid

    commitRegWe(w)   := rob.io.commit.lanes(w).pop && rob.io.commit.lanes(w).rd_write
    commitRegAddr(w) := rob.io.commit.lanes(w).rd
    commitRegData(w) := rob.io.commit.lanes(w).data

    regfile.io.write.en(w)   := commitRegWe(w)
    regfile.io.write.addr(w) := commitRegAddr(w)
    regfile.io.write.data(w) := commitRegData(w)
  }

  private val bpuUpdateValid       = WireDefault(false.B)
  private val bpuUpdatePc          = WireDefault(0.U(p(XLen).W))
  private val bpuUpdateTarget      = WireDefault(0.U(p(XLen).W))
  private val bpuUpdateTaken       = WireDefault(false.B)
  private val bpuUpdatePhtIdx      = WireDefault(0.U(p(GShareGhrWidth).W))
  private val bpuUpdateGhrSnapshot = WireDefault(0.U(p(GShareGhrWidth).W))
  private val bpuUpdateMispredict  = WireDefault(false.B)

  for (w <- 0 until p(IssueWidth)) {
    val isBruCommit           = rob.io.commit.lanes(w).is_branch
    val mispredictedNonBranch = !isBruCommit && rob.io.commit.lanes(w).bpu_pred_taken

    when(rob.io.commit.lanes(w).pop && (isBruCommit || mispredictedNonBranch)) {
      bpuUpdateValid       := true.B
      bpuUpdatePc          := rob.io.commit.lanes(w).pc
      bpuUpdateTarget      := rob.io.commit.lanes(w).bpu_actual_target
      bpuUpdateTaken       := rob.io.commit.lanes(w).bpu_actual_taken
      bpuUpdatePhtIdx      := rob.io.commit.lanes(w).bpu_pht_index
      bpuUpdateGhrSnapshot := rob.io.commit.lanes(w).bpu_ghr_snapshot
      bpuUpdateMispredict  := rob.io.commit.lanes(w).flush_pipeline
    }
  }

  bpu.io.update.update.valid        := bpuUpdateValid
  bpu.io.update.update.pc           := bpuUpdatePc
  bpu.io.update.update.target       := bpuUpdateTarget
  bpu.io.update.update.taken        := bpuUpdateTaken
  bpu.io.update.update.pht_index    := bpuUpdatePhtIdx
  bpu.io.update.update.ghr_snapshot := bpuUpdateGhrSnapshot
  bpu.io.update.update.mispredict   := bpuUpdateMispredict

  for (w <- 0 until p(IssueWidth)) {
    storeBuffer.io.rob.commit(w).valid         := rob.io.commit.lanes(w).pop
    storeBuffer.io.rob.commit(w).bits.is_store := rob.io.commit.lanes(w).is_store
    storeBuffer.io.rob.commit(w).bits.sq_idx   := rob.io.commit.lanes(w).sq_idx
  }

  io.debug.cycle_count   := cycleCount
  io.debug.instret_count := instretCount

  for (w <- 0 until p(IssueWidth)) {
    io.debug.instret(w)  := rob.io.commit.lanes(w).pop
    io.debug.pc(w)       := rob.io.commit.lanes(w).pc
    io.debug.instr(w)    := rob.io.commit.lanes(w).instr
    io.debug.reg_we(w)   := commitRegWe(w)
    io.debug.reg_addr(w) := commitRegAddr(w)
    io.debug.reg_data(w) := commitRegData(w)
  }

  io.debug.branch_taken     := bpuUpdateValid && bpuUpdateTaken
  io.debug.branch_source    := bpuUpdatePc
  io.debug.branch_target    := bpuUpdateTarget
  io.debug.l1_icache_access := l1ICache.upper.resp.fire
  io.debug.l1_icache_miss   := l1ICache.upper.resp.fire && !l1ICache.upper.resp.bits.hit
  io.debug.l1_dcache_access := l1DCache.upper.resp.fire
  io.debug.l1_dcache_miss   := l1DCache.upper.resp.fire && !l1DCache.upper.resp.bits.hit
  io.debug.bpu_mispredict   := (0 until p(IssueWidth))
    .map(w =>
      rob.io.commit.lanes(w).pop &&
        (rob.io.commit.lanes(w).is_branch || (!rob.io.commit
          .lanes(w)
          .is_branch && rob.io.commit.lanes(w).bpu_pred_taken)) &&
        rob.io.commit.lanes(w).flush_pipeline
    )
    .reduce(_ || _)
  io.debug.branch_commit    := PopCount(
    (0 until p(IssueWidth)).map(w => rob.io.commit.lanes(w).pop && rob.io.commit.lanes(w).is_branch)
  )
  io.debug.flush_cycle      := exception.io.redirect.valid
  io.debug.rob_empty        := rob.io.ctrl.empty
  io.debug.issue_count      := PopCount(scheduler.io.dispatch.reqs.map(_.fire))
  io.debug.commit_count     := commitPopCount
  io.debug.frontend_stall   := false.B
  io.debug.backend_stall    := !rob.io.ctrl.empty && commitPopCount === 0.U
}
