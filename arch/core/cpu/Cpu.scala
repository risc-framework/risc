package arch.core.cpu

import arch.core.bpu.Bpu
import arch.core.csr.InterruptLines
import arch.core.decode.Decode
import arch.core.exception.Exception
import arch.core.fupool.FuPool
import arch.core.ifu.Ifu
import arch.core.interrupt.Interrupt
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
import chisel3.util.PopCount

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

  private val cycleCount   = RegInit(0.U(64.W))
  private val instretCount = RegInit(0.U(64.W))

  cycleCount   := cycleCount + 1.U
  instretCount := instretCount + rob.io.debug.commit_count

  // IO
  io.imem <> l1ICache.lower
  io.dmem <> l1DCache.lower
  io.mmio <> memoryArbiter.io.mmio
  io.irq <> interrupt.io.cpu.irq

  // icache
  l1ICache.upper <> ifu.io.icache

  // dcache
  l1DCache.upper <> memoryArbiter.io.dcache

  // bpu
  bpu.io.ifu <> ifu.io.bpu
  bpu.io.rob <> rob.io.bpu

  // ifu
  ifu.io.decode <> decode.io.ifu
  ifu.io.exception <> exception.io.ifu

  // decode
  decode.io.dispatch <> dispatch.io.decode

  // dispatch
  dispatch.io.scheduler <> scheduler.io.dispatch
  dispatch.io.regfile <> regfile.io.dispatch
  dispatch.io.rob <> rob.io.dispatch
  dispatch.io.sb <> storeBuffer.io.dispatch
  dispatch.io.exception <> exception.io.dispatch

  // scheduler
  scheduler.io.fu_pool <> fuPool.io.scheduler
  scheduler.io.exception <> exception.io.scheduler

  // fupool
  fuPool.io.rob <> rob.io.fu_pool
  fuPool.io.memory_arbiter <> memoryArbiter.io.fu_pool
  fuPool.io.sb <> storeBuffer.io.fu_pool
  fuPool.io.exception <> exception.io.fu_pool
  fuPool.io.interrupt <> interrupt.io.fu_pool

  fuPool.io.cpu.cycle   := cycleCount
  fuPool.io.cpu.instret := instretCount
  fuPool.io.cpu.irq     := io.irq

  // rob
  rob.io.regfile <> regfile.io.rob
  rob.io.sb <> storeBuffer.io.rob
  rob.io.exception <> exception.io.rob
  rob.io.flush <> flush.io.rob

  // sb
  storeBuffer.io.memory_arbiter <> memoryArbiter.io.sb
  storeBuffer.io.exception <> exception.io.sb

  // memarb

  // flush
  flush.io.exception <> exception.io.flush

  // exception

  // interrupt
  interrupt.io.exception <> exception.io.interrupt

  // debug
  io.debug.cycle_count   := cycleCount
  io.debug.instret_count := instretCount

  for (w <- 0 until p(IssueWidth)) {
    io.debug.instret(w)  := rob.io.debug.instret(w)
    io.debug.pc(w)       := rob.io.debug.pc(w)
    io.debug.instr(w)    := rob.io.debug.instr(w)
    io.debug.reg_we(w)   := rob.io.debug.reg_we(w)
    io.debug.reg_addr(w) := rob.io.debug.reg_addr(w)
    io.debug.reg_data(w) := rob.io.debug.reg_data(w)
  }

  io.debug.branch_taken     := rob.io.bpu.update.valid && rob.io.bpu.update.taken
  io.debug.branch_source    := rob.io.bpu.update.valid
  io.debug.branch_target    := rob.io.bpu.update.valid && rob.io.bpu.update.taken
  io.debug.l1_icache_access := l1ICache.upper.resp.fire
  io.debug.l1_icache_miss   := l1ICache.upper.resp.fire && !l1ICache.upper.resp.bits.hit
  io.debug.l1_dcache_access := l1DCache.upper.resp.fire
  io.debug.l1_dcache_miss   := l1DCache.upper.resp.fire && !l1DCache.upper.resp.bits.hit
  io.debug.bpu_mispredict   := rob.io.debug.bpu_mispredict
  io.debug.branch_commit    := rob.io.debug.branch_commit
  io.debug.flush_cycle      := exception.io.debug.redirect.valid
  io.debug.rob_empty        := rob.io.debug.empty
  io.debug.issue_count      := PopCount(scheduler.io.dispatch.reqs.map(_.fire))
  io.debug.commit_count     := rob.io.debug.commit_count
  io.debug.frontend_stall   := decode.io.dispatch.lanes
    .map(lane => lane.valid && !lane.ready)
    .reduce(_ || _)
  io.debug.backend_stall    := !rob.io.debug.empty && rob.io.debug.commit_count === 0.U
}
