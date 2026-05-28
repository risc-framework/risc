package arch.core

import arch.core.bpu.Bpu
import arch.core.csr.CoreInterruptIO
import arch.core.decoder.Decoder
import arch.core.fu.{ FunctionalUnitPool, FunctionalUnitType }
import arch.core.ifu.Ifu
import arch.core.lsu.{ MemoryArbiter, StoreBuffer }
import arch.core.regfile.{ Regfile, RegfileUtilsFactory }
import arch.core.rob.ReorderBuffer
import arch.core.scheduler.Scheduler
import arch.configs._
import vcache.CachePortIO
import vcache.nonblocking.{ NonBlockingCache, ReadOnlyNonBlockingCache }
import chisel3._
import chisel3.util.{ log2Ceil, Mux1H, MuxCase, PopCount }

class RiscCore(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_cpu"

  private val regfile_utils = RegfileUtilsFactory.getOrThrow(p(ISA).name)

  val imem = IO(new CachePortIO(Vec(p(IssueWidth), UInt(p(ILen).W)), p(L1ICacheParams)))
  val dmem = IO(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams)))
  val mmio = IO(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams)))
  val irq  = IO(new CoreInterruptIO)

  val bpu            = Module(new Bpu)
  val ifu            = Module(new Ifu)
  val decoders       = Seq.fill(p(IssueWidth))(Module(new Decoder))
  val regfile        = Module(new Regfile)
  val scheduler      = Scheduler()
  val fu_pool        = Module(new FunctionalUnitPool)
  val rob            = Module(new ReorderBuffer)
  val memory_arbiter = Module(new MemoryArbiter)

  scheduler.bind(fu_pool)

  val l1_icache = Module(
    new ReadOnlyNonBlockingCache(Vec(p(IssueWidth), UInt(p(ILen).W)), p(L1ICacheParams))
  )
  val l1_dcache = Module(new NonBlockingCache(UInt(p(XLen).W), p(L1DCacheParams)))

  val debug = if (p(EnableDebug)) Some(IO(new DebugIO)) else None

  val numLoadFUs  = p(FunctionalUnits).count(_.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD)
  val numStoreFUs = p(FunctionalUnits).count(_.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST)
  val numBruFUs   = p(FunctionalUnits).count(_.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_BRU)
  val numCsrFUs   = p(FunctionalUnits).count(_.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR)

  if (numLoadFUs == 0) {
    throw new Exception("LoadFU is mandatory but missing from FunctionalUnits configuration")
  }

  if (numStoreFUs == 0) {
    throw new Exception("StoreFU is mandatory but missing from FunctionalUnits configuration")
  }

  if (numBruFUs == 0) {
    throw new Exception("BruFU is mandatory but missing from FunctionalUnits configuration")
  }

  if (numCsrFUs > 1) {
    throw new Exception("There should be only one CsrFU")
  }

  val store_buffer = Module(new StoreBuffer(numLoadFUs, numStoreFUs))

  val is_flush = Wire(Vec(p(IssueWidth), Bool()))

  for (w <- 0 until p(IssueWidth))
    is_flush(w) := rob.io.commit(w).pop && rob.io.commit(w).flush_pipeline

  val commit_flush_pipeline = is_flush.asUInt.orR
  val commit_flush_target   = Mux1H(is_flush.zipWithIndex.map { case (f, w) =>
    f -> rob.io.commit(w).flush_target
  })

  val async_trap_req =
    if (numCsrFUs > 0)
      (0 until numCsrFUs)
        .map(i => fu_pool.io.csr_trap_request(i) && !fu_pool.io.csr_is_busy(i))
        .reduce(_ || _)
    else false.B
  val async_trap_tgt =
    if (numCsrFUs > 0)
      Mux1H(
        (0 until numCsrFUs).map(i =>
          (fu_pool.io.csr_trap_request(i) && !fu_pool.io.csr_is_busy(i)) -> fu_pool.io
            .csr_trap_target(i)
        )
      )
    else 0.U(p(XLen).W)

  val global_flush = commit_flush_pipeline || async_trap_req
  val redirect_pc  = Mux(async_trap_req, async_trap_tgt, commit_flush_target)

  for (i <- 0 until numLoadFUs) {
    memory_arbiter.ld_mem(i) <> fu_pool.io.ld_mem(i)
    memory_arbiter.ld_mmio(i) <> fu_pool.io.ld_mmio(i)
    fu_pool.io.ld_sb_fwd(i) <> store_buffer.io.fwd(i)
    fu_pool.io.ld_sb_oldest_valid(i) := store_buffer.io.oldestValid
    fu_pool.io.ld_sb_oldest_seq(i)   := store_buffer.io.oldestSeq
  }

  for (i <- 0 until numStoreFUs)
    store_buffer.io.write(i) := fu_pool.io.st_sb_write(i)

  memory_arbiter.store_mem <> store_buffer.io.mem
  memory_arbiter.store_mmio <> store_buffer.io.mmio

  l1_dcache.upper <> memory_arbiter.mem
  mmio <> memory_arbiter.mmio
  dmem <> l1_dcache.lower

  ifu.mem <> l1_icache.upper
  imem <> l1_icache.lower

  store_buffer.io.flush := global_flush

  for (i <- 0 until numCsrFUs)
    fu_pool.io.csr_arch_pc(i) := Mux(rob.io.empty, ifu.if_pc(0), rob.io.commit(0).pc)

  val bpuQueryBase = ifu.fetch_pc & ~(p(IssueWidth) * p(BytesPerInstr) - 1).U(p(XLen).W)

  for (w <- 0 until p(IssueWidth))
    bpu.query_pc(w) := bpuQueryBase + (w * p(PCStep)).U

  bpu.advance_valid := ifu.fetch_fire
  bpu.flush         := global_flush

  ifu.bpu_taken_in        := bpu.taken
  ifu.bpu_target_in       := bpu.target
  ifu.bpu_pht_index_in    := bpu.pht_index
  ifu.bpu_ghr_snapshot_in := bpu.ghr_snapshot

  ifu.take_trap     := global_flush
  ifu.trap_target   := redirect_pc
  ifu.bru_taken     := false.B
  ifu.bru_target    := 0.U
  ifu.bru_not_taken := false.B
  ifu.bru_branch_pc := 0.U

  val rs1s = Wire(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
  val rs2s = Wire(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
  val rds  = Wire(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))

  val is_load  = Wire(Vec(p(IssueWidth), Bool()))
  val is_store = Wire(Vec(p(IssueWidth), Bool()))
  val is_mem   = Wire(Vec(p(IssueWidth), Bool()))

  val decoded_rd_valid = Wire(Vec(p(IssueWidth), Bool()))
  val inst_type        = Wire(Vec(p(IssueWidth), UInt(p(FuTypeWidth).W)))

  for (w <- 0 until p(IssueWidth)) {
    decoders(w).instr := ifu.if_instr(w)

    rs1s(w) := regfile_utils.getRs1(ifu.if_instr(w))
    rs2s(w) := regfile_utils.getRs2(ifu.if_instr(w))
    rds(w)  := regfile_utils.getRd(ifu.if_instr(w))

    regfile.rs1_preg(w) := rs1s(w)
    regfile.rs2_preg(w) := rs2s(w)

    rob.io.rs1_addr(w) := rs1s(w)
    rob.io.rs2_addr(w) := rs2s(w)

    is_load(w)  := decoders(w).decoded.load
    is_store(w) := decoders(w).decoded.store
    is_mem(w)   := decoders(w).decoded.load || decoders(w).decoded.store

    decoded_rd_valid(w) := decoders(w).decoded.rd_valid && regfile_utils.writable(rds(w))

    inst_type(w) := MuxCase(
      FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ALU.index.U(p(FuTypeWidth).W),
      Seq(
        decoders(w).decoded.load  -> FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD.index
          .U(p(FuTypeWidth).W),
        decoders(w).decoded.store -> FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST.index
          .U(p(FuTypeWidth).W),
        decoders(w).decoded.div   -> FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_DIV.index
          .U(p(FuTypeWidth).W),
        decoders(w).decoded.mult  -> FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_MULT.index
          .U(p(FuTypeWidth).W),
        decoders(w).decoded.bru   -> FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_BRU.index
          .U(p(FuTypeWidth).W),
        decoders(w).decoded.csr   -> FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR.index.U(
          p(FuTypeWidth).W
        )
      )
    )
  }

  val kill_mask = Wire(Vec(p(IssueWidth), Bool()))
  kill_mask(0) := false.B

  for (w <- 1 until p(IssueWidth))
    kill_mask(w) := kill_mask(w - 1) || (ifu.if_valid(w - 1) && ifu.if_bpu_pred_taken(w - 1) && ifu
      .if_pc(w) === ifu.if_pc(w - 1) + p(PCStep).U)

  val possibleStoreBeforeOrAt = Wire(Vec(p(IssueWidth), UInt(log2Ceil(p(IssueWidth) + 1).W)))

  for (w <- 0 until p(IssueWidth))
    possibleStoreBeforeOrAt(w) := PopCount(
      (0 to w)
        .map(v => ifu.if_valid(v) && decoders(v).decoded.store && !kill_mask(v) && !global_flush)
    )

  val lane_base_req_ok = Wire(Vec(p(IssueWidth), Bool()))
  val lane_prefix_ok   = Wire(Vec(p(IssueWidth), Bool()))
  val core_valid_req   = Wire(Vec(p(IssueWidth), Bool()))

  for (w <- 0 until p(IssueWidth)) {
    val sqSlotOk = !is_store(w) || possibleStoreBeforeOrAt(w) <= store_buffer.io.freeCount
    lane_base_req_ok(w) := ifu.if_valid(w) && decoders(
      w
    ).decoded.legal && !global_flush && !kill_mask(w) && sqSlotOk && rob.io.enq(w).ready
  }

  lane_prefix_ok(0) := true.B

  for (w <- 1 until p(IssueWidth)) {
    val olderLaneMayBeSkipped   = !ifu.if_valid(w - 1) || kill_mask(w - 1) || global_flush
    val olderLaneCanBePresented = lane_base_req_ok(w - 1)
    lane_prefix_ok(w) := lane_prefix_ok(w - 1) && (olderLaneMayBeSkipped || olderLaneCanBePresented)
  }

  for (w <- 0 until p(IssueWidth))
    core_valid_req(w) := lane_base_req_ok(w) && lane_prefix_ok(w)

  val lane_valid = Wire(Vec(p(IssueWidth), Bool()))
  val ifu_fire   = Wire(Vec(p(IssueWidth), Bool()))

  private def sqWrapAdd(x: UInt, y: UInt): UInt = {
    val idxW = log2Ceil(p(StoreBufferSize))
    val sum  = x +& y
    Mux(sum >= p(StoreBufferSize).U, sum - p(StoreBufferSize).U, sum)(idxW - 1, 0)
  }

  val sq_idx_for_lane = Wire(Vec(p(IssueWidth), UInt(log2Ceil(p(StoreBufferSize)).W)))
  val sq_tail_after   = Wire(Vec(p(IssueWidth) + 1, UInt(log2Ceil(p(StoreBufferSize)).W)))
  val sq_seq_for_lane = Wire(Vec(p(IssueWidth), UInt(64.W)))
  val sq_seq_after    = Wire(Vec(p(IssueWidth) + 1, UInt(64.W)))

  sq_tail_after(0) := store_buffer.io.tail
  sq_seq_after(0)  := store_buffer.io.tailSeq

  for (w <- 0 until p(IssueWidth)) {
    sq_idx_for_lane(w) := sq_tail_after(w)
    sq_seq_for_lane(w) := sq_seq_after(w)

    val allocStore = lane_valid(w) && is_store(w)
    sq_tail_after(w + 1) := Mux(allocStore, sqWrapAdd(sq_tail_after(w), 1.U), sq_tail_after(w))
    sq_seq_after(w + 1)  := sq_seq_after(w) + allocStore.asUInt
  }

  for (w <- 0 until p(IssueWidth)) {
    store_buffer.io.alloc(w).valid        := lane_valid(w) && is_store(w)
    store_buffer.io.alloc(w).bits.sq_idx  := sq_idx_for_lane(w)
    store_buffer.io.alloc(w).bits.sq_seq  := sq_seq_for_lane(w)
    store_buffer.io.alloc(w).bits.rob_tag := rob.io.enq(w).rob_tag
  }

  for (w <- 0 until p(IssueWidth)) {
    store_buffer.io.commit(w).valid := rob.io.commit(w).pop && rob.io.commit(w).is_store
    store_buffer.io.commit(w).bits  := rob.io.commit(w).sq_idx
  }

  val rs1_commit_match = Wire(Vec(p(IssueWidth), Bool()))
  val rs2_commit_match = Wire(Vec(p(IssueWidth), Bool()))
  val rs1_commit_data  = Wire(Vec(p(IssueWidth), UInt(p(XLen).W)))
  val rs2_commit_data  = Wire(Vec(p(IssueWidth), UInt(p(XLen).W)))

  for (w <- 0 until p(IssueWidth)) {
    val match1 = (0 until p(IssueWidth)).map(cw =>
      rob.io.commit(cw).pop && rob.io.commit(cw).rd === rs1s(w) && rs1s(w) =/= 0.U
    )
    val match2 = (0 until p(IssueWidth)).map(cw =>
      rob.io.commit(cw).pop && rob.io.commit(cw).rd === rs2s(w) && rs2s(w) =/= 0.U
    )

    rs1_commit_match(w) := match1.reduce(_ || _)
    rs2_commit_match(w) := match2.reduce(_ || _)
    rs1_commit_data(w)  := Mux1H(match1, rob.io.commit.map(_.data))
    rs2_commit_data(w)  := Mux1H(match2, rob.io.commit.map(_.data))
  }

  for (w <- 0 until p(IssueWidth)) {
    val rs1_bypassed       =
      Mux(rob.io.rs1_bypass(w).valid, rob.io.rs1_bypass(w).data, regfile.rs1_data(w))
    val rs2_bypassed       =
      Mux(rob.io.rs2_bypass(w).valid, rob.io.rs2_bypass(w).data, regfile.rs2_data(w))
    val rs1_fully_bypassed = Mux(rs1_commit_match(w), rs1_commit_data(w), rs1_bypassed)
    val rs2_fully_bypassed = Mux(rs2_commit_match(w), rs2_commit_data(w), rs2_bypassed)
    val dis                = scheduler.dis_reqs(w)

    dis.valid          := core_valid_req(w)
    dis.bits.pc        := ifu.if_pc(w)
    dis.bits.instr     := ifu.if_instr(w)
    dis.bits.fu_type   := inst_type(w)
    dis.bits.fu_id     := 0.U
    dis.bits.uop       := decoders(w).decoded.uop
    dis.bits.imm_type  := decoders(w).decoded.imm_type
    dis.bits.rs1       := rs1s(w)
    dis.bits.rs2       := rs2s(w)
    dis.bits.rd        := Mux(decoded_rd_valid(w), rds(w), 0.U)
    dis.bits.rs1_valid := decoders(w).decoded.rs1_valid
    dis.bits.rs2_valid := decoders(w).decoded.rs2_valid
    dis.bits.rd_valid  := decoded_rd_valid(w)
    dis.bits.rs1_data  := rs1_fully_bypassed
    dis.bits.rs2_data  := rs2_fully_bypassed
    dis.bits.rob_tag   := rob.io.enq(w).rob_tag
    dis.bits.sq_idx    := sq_idx_for_lane(w)
    dis.bits.sq_seq    := sq_seq_for_lane(w)

    lane_valid(w) := dis.fire
  }

  for (w <- 0 until p(IssueWidth)) {
    rob.io.enq(w).valid            := lane_valid(w)
    rob.io.enq(w).pc               := ifu.if_pc(w)
    rob.io.enq(w).instr            := ifu.if_instr(w)
    rob.io.enq(w).rd               := Mux(decoded_rd_valid(w), rds(w), 0.U)
    rob.io.enq(w).pd               := 0.U
    rob.io.enq(w).old_pd           := 0.U
    rob.io.enq(w).is_branch        := decoders(w).decoded.bru
    rob.io.enq(w).is_store         := is_store(w)
    rob.io.enq(w).commit_barrier   := decoders(w).decoded.commit_barrier
    rob.io.enq(w).bpu_pred_taken   := ifu.if_bpu_pred_taken(w)
    rob.io.enq(w).bpu_pred_target  := ifu.if_bpu_pred_target(w)
    rob.io.enq(w).bpu_pht_index    := ifu.if_bpu_pht_index(w)
    rob.io.enq(w).bpu_ghr_snapshot := ifu.if_bpu_ghr_snapshot(w)
    rob.io.enq(w).sq_idx           := sq_idx_for_lane(w)
  }

  for (w <- 0 until p(IssueWidth)) {
    val consumeThisLane = global_flush || kill_mask(w) || lane_valid(w)

    if (w == 0) {
      ifu_fire(w) := ifu.if_valid(w) && consumeThisLane
    } else {
      ifu_fire(w) := ifu.if_valid(w) && ifu_fire(w - 1) && consumeThisLane
    }
  }

  ifu.dispatch_fire := ifu_fire

  scheduler.flush := global_flush
  rob.io.flush    := global_flush

  for (i <- 0 until p(NumFUs)) {
    val wb = fu_pool.io.done(i)

    rob.io.wb(i).valid         := wb.valid
    rob.io.wb(i).rob_tag       := wb.bits.rob_tag
    rob.io.wb(i).data          := wb.bits.result
    rob.io.wb(i).is_bru        := wb.bits.is_bru
    rob.io.wb(i).actual_taken  := wb.bits.actual_taken
    rob.io.wb(i).actual_target := wb.bits.actual_target
    rob.io.wb(i).trap_req      := wb.bits.trap_req
    rob.io.wb(i).trap_target   := wb.bits.trap_target
    rob.io.wb(i).trap_ret      := wb.bits.trap_ret
    rob.io.wb(i).trap_ret_tgt  := wb.bits.trap_ret_tgt
  }

  for (w <- 0 until p(IssueWidth)) {
    rob.io.read_rob_tag(w) := 0.U
    rob.io.commit(w).pop   := rob.io.commit(w).valid

    regfile.write_en(w)   := rob.io.commit(w).pop && regfile_utils.writable(rob.io.commit(w).rd)
    regfile.write_preg(w) := rob.io.commit(w).rd
    regfile.write_data(w) := rob.io.commit(w).data
  }

  val bpu_update_valid        = WireDefault(false.B)
  val bpu_update_pc           = WireDefault(0.U(p(XLen).W))
  val bpu_update_target       = WireDefault(0.U(p(XLen).W))
  val bpu_update_taken        = WireDefault(false.B)
  val bpu_update_pht_idx      = WireDefault(0.U(p(GShareGhrWidth).W))
  val bpu_update_ghr_snapshot = WireDefault(0.U(p(GShareGhrWidth).W))
  val bpu_update_mispredict   = WireDefault(false.B)

  for (w <- 0 until p(IssueWidth)) {
    val is_bru_commit           = rob.io.commit(w).is_branch
    val mispredicted_non_branch = !is_bru_commit && rob.io.commit(w).bpu_pred_taken

    when(rob.io.commit(w).pop && (is_bru_commit || mispredicted_non_branch)) {
      bpu_update_valid        := true.B
      bpu_update_pc           := rob.io.commit(w).pc
      bpu_update_target       := rob.io.commit(w).bpu_actual_target
      bpu_update_taken        := rob.io.commit(w).bpu_actual_taken
      bpu_update_pht_idx      := rob.io.commit(w).bpu_pht_index
      bpu_update_ghr_snapshot := rob.io.commit(w).bpu_ghr_snapshot
      bpu_update_mispredict   := rob.io.commit(w).flush_pipeline
    }
  }

  bpu.update.valid        := bpu_update_valid
  bpu.update.pc           := bpu_update_pc
  bpu.update.target       := bpu_update_target
  bpu.update.taken        := bpu_update_taken
  bpu.update.pht_index    := bpu_update_pht_idx
  bpu.update.ghr_snapshot := bpu_update_ghr_snapshot
  bpu.update.mispredict   := bpu_update_mispredict

  val cycle_count      = RegInit(0.U(64.W))
  val instret_count    = RegInit(0.U(64.W))
  val commit_pop_count = PopCount(rob.io.commit.map(_.pop))

  cycle_count   := cycle_count + 1.U
  instret_count := instret_count + commit_pop_count

  fu_pool.io.csr_cycle   := cycle_count
  fu_pool.io.csr_instret := instret_count

  for (i <- 0 until numCsrFUs) {
    fu_pool.io.csr_irq(i).timer_irq := RegNext(irq.timer_irq, false.B)
    fu_pool.io.csr_irq(i).soft_irq  := RegNext(irq.soft_irq, false.B)
    fu_pool.io.csr_irq(i).ext_irq   := RegNext(irq.ext_irq, false.B)
  }

  if (debug.isDefined) {
    val debug_io = debug.get

    debug_io.cycle_count   := cycle_count
    debug_io.instret_count := instret_count

    for (w <- 0 until p(IssueWidth)) {
      debug_io.instret(w)  := rob.io.commit(w).pop
      debug_io.pc(w)       := rob.io.commit(w).pc
      debug_io.instr(w)    := rob.io.commit(w).instr
      debug_io.reg_we(w)   := regfile.write_en(w)
      debug_io.reg_addr(w) := regfile.write_preg(w)
      debug_io.reg_data(w) := regfile.write_data(w)
    }

    debug_io.branch_taken  := bpu_update_valid && bpu_update_taken
    debug_io.branch_source := bpu_update_pc
    debug_io.branch_target := bpu_update_target

    debug_io.l1_icache_access := RegNext(l1_icache.upper.req.fire)
    debug_io.l1_icache_miss   := !l1_icache.upper.resp.bits.hit
    debug_io.l1_dcache_access := RegNext(l1_dcache.upper.req.fire)
    debug_io.l1_dcache_miss   := !l1_dcache.upper.resp.bits.hit

    debug_io.bpu_mispredict := (0 until p(IssueWidth))
      .map(w =>
        rob.io.commit(w).pop && (rob.io.commit(w).is_branch || (!rob.io
          .commit(w)
          .is_branch && rob.io.commit(w).bpu_pred_taken)) && rob.io.commit(w).flush_pipeline
      )
      .reduce(_ || _)
    debug_io.branch_commit  := PopCount(
      (0 until p(IssueWidth)).map(w => rob.io.commit(w).pop && rob.io.commit(w).is_branch)
    )
    debug_io.flush_cycle    := global_flush
    debug_io.rob_empty      := rob.io.empty
    debug_io.issue_count    := PopCount(lane_valid)
    debug_io.commit_count   := commit_pop_count
    debug_io.frontend_stall := lane_base_req_ok(0) && !lane_valid(0)
    debug_io.backend_stall  := !rob.io.empty && commit_pop_count === 0.U
  }
}
