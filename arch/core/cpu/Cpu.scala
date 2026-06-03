package arch.core.cpu

import arch.core.bpu.Bpu
import arch.core.csr.{ CsrTrapView, InterruptLines }
import arch.core.decode.Decode
import arch.core.exception.{ Exception, RedirectBundle }
import arch.core.fupool.FuPool
import arch.core.ifu.Ifu
import arch.core.interrupt.{ Interrupt, TrapCandidate }
import arch.core.memarb.MemoryArbiter
import arch.core.regfile.Regfile
import arch.core.rob.Rob
import arch.core.sb.StoreBuffer
import arch.core.scheduler.Scheduler
import arch.configs._
import vcache.CachePortIO
import vcache.nonblocking.{ NonBlockingCache, ReadOnlyNonBlockingCache }
import vutils.graph.{ Node, NodeType }
import chisel3._
import chisel3.util.{ Mux1H, PopCount, log2Ceil }

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

  private val isFlush = Wire(Vec(p(IssueWidth), Bool()))

  for (w <- 0 until p(IssueWidth))
    isFlush(w) := rob.io.commit.lanes(w).pop && rob.io.commit.lanes(w).flush_pipeline

  private val commitFlushPipeline = isFlush.asUInt.orR
  private val commitFlushTarget   = Mux1H(isFlush.zipWithIndex.map { case (f, w) =>
    f -> rob.io.commit.lanes(w).flush_target
  })

  private val commitRedirect = Wire(new RedirectBundle)

  commitRedirect.valid  := commitFlushPipeline
  commitRedirect.target := commitFlushTarget

  private val archPc = Mux(rob.io.ctrl.empty, ifu.io.dispatch.fetch_pc, rob.io.commit.lanes(0).pc)

  exception.io.commitRedirect := commitRedirect
  exception.io.archPc         := archPc

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

  private val globalFlush = exception.io.redirect.valid
  private val redirectPc  = exception.io.redirect.target

  ifu.io.redirect.valid  := globalFlush
  ifu.io.redirect.target := redirectPc

  scheduler.io.ctrl.flush   := globalFlush
  fuPool.io.fu.flush        := globalFlush
  rob.io.ctrl.flush         := globalFlush
  storeBuffer.io.ctrl.flush := globalFlush

  decode.io.in <> ifu.io.dispatch.out

  private val rs1s = Wire(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
  private val rs2s = Wire(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
  private val rds  = Wire(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))

  private val isStore = Wire(Vec(p(IssueWidth), Bool()))

  for (w <- 0 until p(IssueWidth)) {
    val dec = decode.io.out(w).bits

    rs1s(w) := dec.rs1
    rs2s(w) := dec.rs2
    rds(w)  := dec.rd

    regfile.io.read.rs1_addr(w) := dec.rs1
    regfile.io.read.rs2_addr(w) := dec.rs2

    rob.io.bypass.rs1_addr(w) := dec.rs1
    rob.io.bypass.rs2_addr(w) := dec.rs2

    isStore(w) := dec.isStore
  }

  private val killMask = Wire(Vec(p(IssueWidth), Bool()))

  killMask(0) := false.B

  for (w <- 1 until p(IssueWidth)) {
    val prev = decode.io.out(w - 1)
    val cur  = decode.io.out(w)

    killMask(w) := killMask(w - 1) || (
      prev.valid &&
        prev.bits.bpu_pred_taken &&
        cur.bits.pc === prev.bits.pc + p(PCStep).U
    )
  }

  private val possibleStoreBeforeOrAt = Wire(
    Vec(p(IssueWidth), UInt(log2Ceil(p(IssueWidth) + 1).W))
  )

  for (w <- 0 until p(IssueWidth))
    possibleStoreBeforeOrAt(w) := PopCount(
      (0 to w).map(v => decode.io.out(v).valid && isStore(v) && !killMask(v) && !globalFlush)
    )

  private val laneBaseReqOk = Wire(Vec(p(IssueWidth), Bool()))
  private val lanePrefixOk  = Wire(Vec(p(IssueWidth), Bool()))
  private val coreValidReq  = Wire(Vec(p(IssueWidth), Bool()))

  for (w <- 0 until p(IssueWidth)) {
    val dec      = decode.io.out(w).bits
    val sqSlotOk = !isStore(w) || possibleStoreBeforeOrAt(w) <= storeBuffer.io.state.freeCount

    laneBaseReqOk(w) := decode.io.out(w).valid && dec.legal && !globalFlush && !killMask(
      w
    ) && sqSlotOk && rob.io.enq.lanes(w).ready
  }

  lanePrefixOk(0) := true.B

  for (w <- 1 until p(IssueWidth)) {
    val olderLaneMayBeSkipped   = !decode.io.out(w - 1).valid || killMask(w - 1) || globalFlush
    val olderLaneCanBePresented = laneBaseReqOk(w - 1)

    lanePrefixOk(w) := lanePrefixOk(w - 1) && (olderLaneMayBeSkipped || olderLaneCanBePresented)
  }

  for (w <- 0 until p(IssueWidth))
    coreValidReq(w) := laneBaseReqOk(w) && lanePrefixOk(w)

  private def sqWrapAdd(x: UInt, y: UInt): UInt = {
    val idxW = log2Ceil(p(StoreBufferSize))
    val sum  = x +& y

    Mux(sum >= p(StoreBufferSize).U, sum - p(StoreBufferSize).U, sum)(idxW - 1, 0)
  }

  private val sqIdxForLane = Wire(Vec(p(IssueWidth), UInt(log2Ceil(p(StoreBufferSize)).W)))
  private val sqTailAfter  = Wire(Vec(p(IssueWidth) + 1, UInt(log2Ceil(p(StoreBufferSize)).W)))
  private val sqSeqForLane = Wire(Vec(p(IssueWidth), UInt(64.W)))
  private val sqSeqAfter   = Wire(Vec(p(IssueWidth) + 1, UInt(64.W)))

  sqTailAfter(0) := storeBuffer.io.state.tail
  sqSeqAfter(0)  := storeBuffer.io.state.tailSeq

  for (w <- 0 until p(IssueWidth)) {
    sqIdxForLane(w) := sqTailAfter(w)
    sqSeqForLane(w) := sqSeqAfter(w)

    val allocStore = scheduler.io.dispatch.reqs(w).fire && isStore(w)

    sqTailAfter(w + 1) := Mux(allocStore, sqWrapAdd(sqTailAfter(w), 1.U), sqTailAfter(w))
    sqSeqAfter(w + 1)  := sqSeqAfter(w) + allocStore.asUInt
  }

  for (w <- 0 until p(IssueWidth)) {
    storeBuffer.io.alloc.ports(w).valid        := scheduler.io.dispatch.reqs(w).fire && isStore(w)
    storeBuffer.io.alloc.ports(w).bits.sq_idx  := sqIdxForLane(w)
    storeBuffer.io.alloc.ports(w).bits.sq_seq  := sqSeqForLane(w)
    storeBuffer.io.alloc.ports(w).bits.rob_tag := rob.io.enq.lanes(w).rob_tag
  }

  for (w <- 0 until p(IssueWidth)) {
    storeBuffer.io.commit
      .ports(w)
      .valid                            := rob.io.commit.lanes(w).pop && rob.io.commit.lanes(w).is_store
    storeBuffer.io.commit.ports(w).bits := rob.io.commit.lanes(w).sq_idx
  }

  private val rs1CommitMatch = Wire(Vec(p(IssueWidth), Bool()))
  private val rs2CommitMatch = Wire(Vec(p(IssueWidth), Bool()))
  private val rs1CommitData  = Wire(Vec(p(IssueWidth), UInt(p(XLen).W)))
  private val rs2CommitData  = Wire(Vec(p(IssueWidth), UInt(p(XLen).W)))

  for (w <- 0 until p(IssueWidth)) {
    val match1 = (0 until p(IssueWidth)).map(cw =>
      rob.io.commit.lanes(cw).pop && rob.io.commit.lanes(cw).rd === rs1s(w) && rob.io.commit
        .lanes(cw)
        .rd_write
    )
    val match2 = (0 until p(IssueWidth)).map(cw =>
      rob.io.commit.lanes(cw).pop && rob.io.commit.lanes(cw).rd === rs2s(w) && rob.io.commit
        .lanes(cw)
        .rd_write
    )

    rs1CommitMatch(w) := match1.reduce(_ || _)
    rs2CommitMatch(w) := match2.reduce(_ || _)
    rs1CommitData(w)  := Mux1H(match1, rob.io.commit.lanes.map(_.data))
    rs2CommitData(w)  := Mux1H(match2, rob.io.commit.lanes.map(_.data))
  }

  for (w <- 0 until p(IssueWidth)) {
    val dec              = decode.io.out(w).bits
    val rs1Bypassed      = Mux(
      rob.io.bypass.rs1_bypass(w).valid,
      rob.io.bypass.rs1_bypass(w).data,
      regfile.io.read.rs1_data(w)
    )
    val rs2Bypassed      = Mux(
      rob.io.bypass.rs2_bypass(w).valid,
      rob.io.bypass.rs2_bypass(w).data,
      regfile.io.read.rs2_data(w)
    )
    val rs1FullyBypassed = Mux(rs1CommitMatch(w), rs1CommitData(w), rs1Bypassed)
    val rs2FullyBypassed = Mux(rs2CommitMatch(w), rs2CommitData(w), rs2Bypassed)
    val dis              = scheduler.io.dispatch.reqs(w)

    dis.valid         := coreValidReq(w)
    dis.bits.pc       := dec.pc
    dis.bits.instr    := dec.instr
    dis.bits.fu_type  := dec.fu_type
    dis.bits.fu_id    := 0.U
    dis.bits.uop      := dec.uop
    dis.bits.imm      := dec.imm
    dis.bits.rs1      := dec.rs1
    dis.bits.rs2      := dec.rs2
    dis.bits.rd       := dec.rd
    dis.bits.rd_write := dec.rd_write
    dis.bits.rs1_read := dec.rs1_read
    dis.bits.rs2_read := dec.rs2_read
    dis.bits.rd_write := dec.rd_write
    dis.bits.rs1_data := rs1FullyBypassed
    dis.bits.rs2_data := rs2FullyBypassed
    dis.bits.rob_tag  := rob.io.enq.lanes(w).rob_tag
    dis.bits.sq_idx   := sqIdxForLane(w)
    dis.bits.sq_seq   := sqSeqForLane(w)
  }

  for (w <- 0 until p(IssueWidth)) {
    val dec = decode.io.out(w).bits

    rob.io.enq.lanes(w).valid            := scheduler.io.dispatch.reqs(w).fire
    rob.io.enq.lanes(w).pc               := dec.pc
    rob.io.enq.lanes(w).instr            := dec.instr
    rob.io.enq.lanes(w).rd               := dec.rd
    rob.io.enq.lanes(w).rd_write         := dec.rd_write
    rob.io.enq.lanes(w).pd               := 0.U
    rob.io.enq.lanes(w).old_pd           := 0.U
    rob.io.enq.lanes(w).is_branch        := dec.isBru
    rob.io.enq.lanes(w).is_store         := isStore(w)
    rob.io.enq.lanes(w).commit_barrier   := dec.commit_barrier
    rob.io.enq.lanes(w).bpu_pred_taken   := dec.bpu_pred_taken
    rob.io.enq.lanes(w).bpu_pred_target  := dec.bpu_pred_target
    rob.io.enq.lanes(w).bpu_pht_index    := dec.bpu_pht_index
    rob.io.enq.lanes(w).bpu_ghr_snapshot := dec.bpu_ghr_snapshot
    rob.io.enq.lanes(w).sq_idx           := sqIdxForLane(w)
  }

  private val decodeReady = Wire(Vec(p(IssueWidth), Bool()))

  for (w <- 0 until p(IssueWidth)) {
    val consumeThisLane = globalFlush || killMask(w) || scheduler.io.dispatch.reqs(w).fire

    if (w == 0) decodeReady(w) := consumeThisLane
    else decodeReady(w)        := decode.io.out(w - 1).fire && consumeThisLane

    decode.io.out(w).ready := decodeReady(w)
  }

  private val commitRegWe   = Wire(Vec(p(IssueWidth), Bool()))
  private val commitRegAddr = Wire(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
  private val commitRegData = Wire(Vec(p(IssueWidth), UInt(p(XLen).W)))

  for (w <- 0 until p(IssueWidth)) {
    rob.io.rename.read_rob_tag(w) := 0.U
    rob.io.commit.lanes(w).pop    := rob.io.commit.lanes(w).valid

    commitRegWe(w)   := rob.io.commit.lanes(w).pop && rob.io.commit.lanes(w).rd =/= 0.U
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
  io.debug.l1_icache_miss   := !l1ICache.upper.resp.bits.hit
  io.debug.l1_dcache_access := l1DCache.upper.resp.fire
  io.debug.l1_dcache_miss   := !l1DCache.upper.resp.bits.hit
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
  io.debug.flush_cycle      := globalFlush
  io.debug.rob_empty        := rob.io.ctrl.empty
  io.debug.issue_count      := PopCount(scheduler.io.dispatch.reqs.map(_.fire))
  io.debug.commit_count     := commitPopCount
  io.debug.frontend_stall   := laneBaseReqOk(0) && !scheduler.io.dispatch.reqs.map(_.fire)(0)
  io.debug.backend_stall    := !rob.io.ctrl.empty && commitPopCount === 0.U
}
