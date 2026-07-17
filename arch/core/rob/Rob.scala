package arch.core.rob

import arch.configs._
import arch.core.bpu.{ BpuBranchKind, BpuHistoryRepair, BpuUpdate }
import arch.core.ifu.RedirectInfo
import arch.core.bru.BruResolveBundle
import arch.core.dispatch.{ DispatchRobPacket, DispatchRobResp }
import arch.core.exception.ExceptionSyncReq
import arch.core.fupool.FuResp
import arch.core.regfile.RegfileWrite
import arch.core.sb.{ StoreBufferAllocReq, StoreBufferAllocStatus }
import vutils.graph.Node
import chisel3._
import chisel3.util.{ Mux1H, PopCount, PriorityEncoder, UIntToOH, log2Ceil }

class Rob(implicit p: Parameters) extends Node[Parameters]("rob") {
  val dispatchReq       = inDVec[DispatchRobPacket](p => p(IssueWidth))
  val dispatchResp      = outVec[DispatchRobResp](p => p(IssueWidth))
  val fuDone            = inDVec[FuResp](p => p(NumFUs))
  val bruResolved       = inVVec[BruResolveBundle](p => p(NumBRUs))
  val rdWrite           = outVVec[RegfileWrite](p => p(CommitWidth))
  val sbAllocStatus     = in[StoreBufferAllocStatus]
  val sbAlloc           = outVVec[StoreBufferAllocReq](p => p(IssueWidth))
  val sbCommit          = outVVec[RobSbCommit](p => p(CommitWidth))
  val bpuUpdate         = out[BpuUpdate]
  val committedRedirect = outVec[RedirectInfo](p => p(CommitWidth))
  val committedSync     = outVec[ExceptionSyncReq](p => p(CommitWidth))
  val earlyRedirect     = out[RedirectInfo]
  val earlyHistoryRepair = out[BpuHistoryRepair]
  val earlyRedirectPending = out[Bool]
  val preserveFrontend  = out[Bool]
  val flush             = in[Bool]
  val debug             = out[RobDebugInfo]

  private val CntW   = log2Ceil(p(RobSize) + 1)
  private val SqIdxW = log2Ceil(p(StoreBufferSize))
  private val SqCntW = log2Ceil(p(StoreBufferSize) + 1)

  private val buffer = RegInit(VecInit(Seq.fill(p(RobSize))(0.U.asTypeOf(new RobEntry))))
  private val head   = RegInit(0.U(p(RobTagWidth).W))
  private val tail   = RegInit(0.U(p(RobTagWidth).W))
  private val count  = RegInit(0.U(CntW.W))

  // A resolved mispredict may redirect only the fetch side before the branch
  // reaches the ROB head.  Correct-path instructions are buffered, but held
  // out of dispatch until the normal precise commit-time backend flush.
  private val earlyRedirectPendingReg = RegInit(false.B)
  private val earlyRedirectTag        = RegInit(0.U(p(RobTagWidth).W))
  private val earlyRedirectValidReg   = RegInit(false.B)
  private val earlyRedirectTargetReg  = RegInit(0.U(p(XLen).W))
  private val earlyRedirectIssuedReg  = RegInit(false.B)
  private val earlyRedirectConditionalReg = RegInit(false.B)
  private val earlyRedirectGhrReg     = RegInit(0.U(p(BpuHistoryWidth).W))
  private val earlyRedirectTakenReg   = RegInit(false.B)

  earlyRedirectValidReg := false.B

  for (i <- 0 until p(NumFUs))
    fuDone.in.lanes(i).ready := true.B

  private def wrapAdd(x: UInt, y: UInt): UInt = {
    val sum = x +& y
    if ((p(RobSize) & (p(RobSize) - 1)) == 0)
      sum(p(RobTagWidth) - 1, 0)
    else
      Mux(sum >= p(RobSize).U, sum - p(RobSize).U, sum)(p(RobTagWidth) - 1, 0)
  }

  private def wrapSqAdd(x: UInt, y: UInt): UInt = {
    val sum = x +& y
    Mux(sum >= p(StoreBufferSize).U, sum - p(StoreBufferSize).U, sum)(SqIdxW - 1, 0)
  }

  private def indexFromNewest(distance: Int): UInt = {
    val sub = distance + 1
    if ((p(RobSize) & (p(RobSize) - 1)) == 0)
      (tail - sub.U)(p(RobTagWidth) - 1, 0)
    else
      Mux(tail >= sub.U, tail - sub.U, tail + p(RobSize).U - sub.U)(p(RobTagWidth) - 1, 0)
  }

  private def bypassNewest(rs: UInt): (Bool, UInt, Bool, UInt) = {
    val matchVec = Wire(Vec(p(RobSize), Bool()))
    val readyVec = Wire(Vec(p(RobSize), Bool()))
    val dataVec  = Wire(Vec(p(RobSize), UInt(p(XLen).W)))
    val tagVec   = Wire(Vec(p(RobSize), UInt(p(RobTagWidth).W)))

    for (d <- 0 until p(RobSize)) {
      val idx   = indexFromNewest(d)
      val entry = buffer(idx)

      matchVec(d) := entry.valid && entry.rd_write && entry.rd === rs
      readyVec(d) := matchVec(d) && entry.ready
      dataVec(d)  := entry.data
      tagVec(d)   := idx
    }

    val anyMatch    = matchVec.asUInt.orR
    val newest      = PriorityEncoder(matchVec)
    val newestOH    = UIntToOH(newest, p(RobSize))
    val newestReady = anyMatch && Mux1H(newestOH, readyVec)
    val newestData  = Mux(anyMatch, Mux1H(newestOH, dataVec), 0.U(p(XLen).W))
    val newestTag   = Mux(anyMatch, Mux1H(newestOH, tagVec), 0.U(p(RobTagWidth).W))
    val pending     = anyMatch && !newestReady

    (newestReady, newestData, pending, newestTag)
  }

  for (i <- 0 until p(NumFUs))
    when(fuDone.in.lanes(i).fire) {
      val done             = fuDone.in.lanes(i).bits
      val idx              = done.rob_tag
      val oldPc            = buffer(idx).pc
      val nonBruMispredict = !buffer(idx).is_branch && buffer(idx).pred_taken
      val nonBruRedirect   = oldPc + p(PCStep).U(p(XLen).W)

      buffer(idx).ready := true.B
      buffer(idx).data  := done.result

      when(nonBruMispredict) {
        buffer(idx).actual_taken   := false.B
        buffer(idx).actual_target  := nonBruRedirect
        buffer(idx).flush_pipeline := true.B
        buffer(idx).flush_target   := nonBruRedirect
      }

      when(done.trap_req) {
        buffer(idx).flush_pipeline := true.B
        buffer(idx).flush_target   := done.trap_target
        buffer(idx).sync_valid     := true.B
        buffer(idx).sync_kind      := done.trap_kind
      }
    }

  private val bruMispredictVec = Wire(Vec(p(NumBRUs), Bool()))
  private val bruActualTarget  = Wire(Vec(p(NumBRUs), UInt(p(XLen).W)))

  for (i <- 0 until p(NumBRUs)) {
    val resolved      = bruResolved.in.lanes(i).bits
    val idx           = resolved.rob_tag
    val oldPredTaken  = buffer(idx).pred_taken
    val oldPredTarget = buffer(idx).pred_target
    val actualTarget  = Mux(resolved.taken, resolved.target, resolved.fallthrough)
    val bruMispredict = resolved.taken =/= oldPredTaken || actualTarget =/= oldPredTarget

    bruMispredictVec(i) := bruResolved.in.lanes(i).valid && buffer(idx).valid && bruMispredict
    bruActualTarget(i)  := actualTarget

    when(bruResolved.in.lanes(i).valid) {

      buffer(idx).actual_taken  := resolved.taken
      buffer(idx).actual_target := actualTarget

      when(bruMispredict) {
        buffer(idx).flush_pipeline := true.B
        buffer(idx).flush_target   := actualTarget
      }
    }
  }

  private val earlyRedirectAny  = bruMispredictVec.asUInt.orR
  private val earlyRedirectSlot = PriorityEncoder(bruMispredictVec.asUInt)

  when(earlyRedirectAny && !earlyRedirectPendingReg && !flush.in) {
    earlyRedirectPendingReg := true.B
    earlyRedirectTag        := bruResolved.in.lanes(earlyRedirectSlot).bits.rob_tag
    earlyRedirectTargetReg  := bruActualTarget(earlyRedirectSlot)
    earlyRedirectValidReg   := true.B
    earlyRedirectIssuedReg  := false.B
    earlyRedirectConditionalReg :=
      buffer(bruResolved.in.lanes(earlyRedirectSlot).bits.rob_tag).branch_kind === BpuBranchKind.BRANCH
    earlyRedirectGhrReg :=
      buffer(bruResolved.in.lanes(earlyRedirectSlot).bits.rob_tag).ghr_snapshot
    earlyRedirectTakenReg := bruResolved.in.lanes(earlyRedirectSlot).bits.taken
  }

  private val earlyRedirectCanIssue = earlyRedirectValidReg && !flush.in

  when(earlyRedirectCanIssue) {
    earlyRedirectIssuedReg := true.B
  }

  earlyRedirect.out.valid  := earlyRedirectCanIssue
  earlyRedirect.out.target := earlyRedirectTargetReg
  earlyHistoryRepair.out.valid := earlyRedirectCanIssue && earlyRedirectConditionalReg
  earlyHistoryRepair.out.ghr_snapshot := earlyRedirectGhrReg
  earlyHistoryRepair.out.taken := earlyRedirectTakenReg
  earlyRedirectPending.out := earlyRedirectPendingReg

  private val commitPrefix = Wire(Vec(p(CommitWidth) + 1, Bool()))
  private val commitIndex  = Wire(Vec(p(CommitWidth), UInt(p(RobTagWidth).W)))
  private val commitInfo   = Wire(Vec(p(CommitWidth), new RobCommitInfo))
  private val commitPops   = Wire(Vec(p(CommitWidth), Bool()))

  commitPrefix(0) := true.B

  for (w <- 0 until p(CommitWidth)) {
    commitIndex(w) := wrapAdd(head, w.U)

    val entry       = buffer(commitIndex(w))
    val hasEntry    = count > w.U
    val committable = hasEntry && entry.valid && entry.ready && commitPrefix(w)

    commitInfo(w).pc                := entry.pc
    commitInfo(w).instr             := entry.instr
    commitInfo(w).rd                := entry.rd
    commitInfo(w).rd_write          := entry.rd_write
    commitInfo(w).data              := entry.data
    commitInfo(w).flush_pipeline    := entry.flush_pipeline
    commitInfo(w).flush_target      := entry.flush_target
    commitInfo(w).is_branch         := entry.is_branch
    commitInfo(w).is_store          := entry.is_store
    commitInfo(w).commit_barrier    := entry.commit_barrier
    commitInfo(w).bpu_btb_hit       := entry.btb_hit
    commitInfo(w).bpu_pred_taken    := entry.pred_taken
    commitInfo(w).bpu_pred_target   := entry.pred_target
    commitInfo(w).bpu_actual_taken  := entry.actual_taken
    commitInfo(w).bpu_actual_target := entry.actual_target
    commitInfo(w).bpu_pht_index     := entry.pht_index
    commitInfo(w).bpu_ghr_snapshot  := entry.ghr_snapshot
    commitInfo(w).bpu_provider      := entry.provider
    commitInfo(w).bpu_alt_taken     := entry.alt_taken
    commitInfo(w).bpu_branch_kind   := entry.branch_kind
    commitInfo(w).sq_idx            := entry.sq_idx
    commitInfo(w).sync_valid        := entry.sync_valid
    commitInfo(w).sync_kind         := entry.sync_kind

    commitPops(w) := committable

    val stopYoungerCommit = entry.flush_pipeline || entry.commit_barrier

    commitPrefix(w + 1) := committable && !stopYoungerCommit
  }

  private val commitCount               = PopCount(commitPops)
  private val committedEarlyRedirect    = earlyRedirectPendingReg && earlyRedirectIssuedReg &&
    (0 until p(CommitWidth))
    .map(w =>
      commitPops(w) && commitIndex(w) === earlyRedirectTag && commitInfo(w).flush_pipeline &&
        !commitInfo(w).sync_valid
    )
    .reduce(_ || _)

  preserveFrontend.out := committedEarlyRedirect

  when(committedEarlyRedirect || flush.in) {
    earlyRedirectPendingReg := false.B
    earlyRedirectIssuedReg  := false.B
  }

  private val availableSlots            = p(RobSize).U(CntW.W) - count
  private val availableSlotsAfterCommit = availableSlots + commitCount
  private val laneActive                = Wire(Vec(p(IssueWidth), Bool()))
  private val laneIsStore               = Wire(Vec(p(IssueWidth), Bool()))
  private val laneCanReserve            = Wire(Vec(p(IssueWidth), Bool()))
  private val robUsed                   = Wire(Vec(p(IssueWidth) + 1, UInt(CntW.W)))
  private val sqUsed                    = Wire(Vec(p(IssueWidth) + 1, UInt(SqCntW.W)))
  private val enqFire                   = Wire(Vec(p(IssueWidth), Bool()))
  private val enqOffset                 = Wire(Vec(p(IssueWidth), UInt(p(RobTagWidth).W)))
  private val enqIdx                    = Wire(Vec(p(IssueWidth), UInt(p(RobTagWidth).W)))
  private val enqSqIdx                  = Wire(Vec(p(IssueWidth), UInt(SqIdxW.W)))
  private val enqSqSeq                  = Wire(Vec(p(IssueWidth), UInt(p(StoreSeqWidth).W)))

  robUsed(0) := 0.U
  sqUsed(0)  := 0.U

  for (w <- 0 until p(IssueWidth)) {
    val pkt           = dispatchReq.in.lanes(w).bits
    val olderRobUsed  = Wire(UInt(CntW.W))
    val olderSqUsed   = Wire(UInt(SqCntW.W))
    val robCanReserve = !laneActive(w) || availableSlotsAfterCommit > robUsed(w)
    val sqCanReserve  = !laneIsStore(w) || sbAllocStatus.in.free_count > sqUsed(w)

    olderRobUsed := robUsed(w)
    olderSqUsed  := sqUsed(w)

    laneActive(w)     := pkt.active && pkt.decoded.legal && !flush.in
    laneIsStore(w)    := laneActive(w) && pkt.decoded.isStore
    laneCanReserve(w) := laneActive(w) && robCanReserve && sqCanReserve

    dispatchReq.in.lanes(w).ready := robCanReserve && sqCanReserve

    robUsed(w + 1) := robUsed(w) + laneCanReserve(w).asUInt
    sqUsed(w + 1)  := sqUsed(w) + (laneCanReserve(w) && pkt.decoded.isStore).asUInt

    enqFire(w)   := dispatchReq.in.lanes(w).valid && dispatchReq.in.lanes(w).ready && !flush.in
    enqOffset(w) := olderRobUsed(p(RobTagWidth) - 1, 0)
    enqIdx(w)    := wrapAdd(tail, enqOffset(w))
    enqSqIdx(w)  := wrapSqAdd(sbAllocStatus.in.tail, olderSqUsed)
    enqSqSeq(w)  := sbAllocStatus.in.tail_seq + olderSqUsed

    dispatchResp.out.lanes(w).rob_tag := enqIdx(w)
    dispatchResp.out.lanes(w).sq_idx  := enqSqIdx(w)
    dispatchResp.out.lanes(w).sq_seq  := enqSqSeq(w)

    sbAlloc.out.lanes(w).valid       := enqFire(w) && pkt.decoded.isStore
    sbAlloc.out.lanes(w).bits.sq_idx := enqSqIdx(w)
    sbAlloc.out.lanes(w).bits.sq_seq := enqSqSeq(w)
  }

  private val enqCount = PopCount(enqFire)

  for (i <- 0 until p(RobSize)) {
    val retireEntry = (0 until p(CommitWidth))
      .map(w => commitPops(w) && commitIndex(w) === i.U)
      .reduce(_ || _)

    when(retireEntry) {
      buffer(i).valid := false.B
    }
  }

  for (w <- 0 until p(IssueWidth))
    when(enqFire(w)) {
      val idx = enqIdx(w)
      val pkt = dispatchReq.in.lanes(w).bits
      val dec = pkt.decoded

      buffer(idx).valid          := true.B
      buffer(idx).ready          := false.B
      buffer(idx).pc             := dec.pc
      buffer(idx).instr          := dec.instr
      buffer(idx).rd             := dec.rd
      buffer(idx).rd_write       := dec.rd_write
      buffer(idx).data           := 0.U
      buffer(idx).fu_type        := dec.fu_type
      buffer(idx).is_branch      := dec.isBru
      buffer(idx).is_store       := dec.isStore
      buffer(idx).commit_barrier := dec.commit_barrier
      buffer(idx).btb_hit        := dec.bpu_btb_hit
      buffer(idx).pred_taken     := dec.bpu_pred_taken
      buffer(idx).pred_target    := dec.bpu_pred_target
      buffer(idx).pht_index      := dec.bpu_pht_index
      buffer(idx).ghr_snapshot   := dec.bpu_ghr_snapshot
      buffer(idx).provider       := dec.bpu_provider
      buffer(idx).alt_taken      := dec.bpu_alt_taken
      buffer(idx).branch_kind    := dec.bpu_branch_kind
      buffer(idx).actual_taken   := false.B
      buffer(idx).actual_target  := 0.U
      buffer(idx).flush_pipeline := false.B
      buffer(idx).flush_target   := 0.U
      buffer(idx).sq_idx         := enqSqIdx(w)
      buffer(idx).sync_valid     := false.B
      buffer(idx).sync_kind      := 0.U
    }

  head  := wrapAdd(head, commitCount)
  tail  := wrapAdd(tail, enqCount)
  count := count + enqCount - commitCount

  when(flush.in) {
    head  := 0.U
    tail  := 0.U
    count := 0.U

    for (i <- 0 until p(RobSize))
      buffer(i).valid := false.B
  }

  for (w <- 0 until p(IssueWidth)) {
    val dec = dispatchReq.in.lanes(w).bits.decoded

    val (rs1Valid, rs1Data, rs1Pending, rs1Tag) = bypassNewest(dec.rs1)
    val (rs2Valid, rs2Data, rs2Pending, rs2Tag) = bypassNewest(dec.rs2)

    dispatchResp.out.lanes(w).rs1_bypass_valid   := rs1Valid
    dispatchResp.out.lanes(w).rs1_bypass_data    := rs1Data
    dispatchResp.out.lanes(w).rs1_bypass_pending := rs1Pending
    dispatchResp.out.lanes(w).rs1_bypass_tag     := rs1Tag

    dispatchResp.out.lanes(w).rs2_bypass_valid   := rs2Valid
    dispatchResp.out.lanes(w).rs2_bypass_data    := rs2Data
    dispatchResp.out.lanes(w).rs2_bypass_pending := rs2Pending
    dispatchResp.out.lanes(w).rs2_bypass_tag     := rs2Tag
  }

  for (w <- 0 until p(CommitWidth)) {
    val lane = commitInfo(w)

    rdWrite.out.lanes(w).valid     := commitPops(w) && lane.rd_write && !lane.sync_valid
    rdWrite.out.lanes(w).bits.addr := lane.rd
    rdWrite.out.lanes(w).bits.data := lane.data

    sbCommit.out.lanes(w).valid         := commitPops(w) && !lane.sync_valid
    sbCommit.out.lanes(w).bits.is_store := lane.is_store
    sbCommit.out.lanes(w).bits.sq_idx   := lane.sq_idx
  }

  private val bpuUpdateWire = WireDefault(0.U.asTypeOf(new BpuUpdate))

  for (w <- 0 until p(CommitWidth)) {
    val lane               = commitInfo(w)
    val predTakenNonBranch = !lane.is_branch && lane.bpu_pred_taken
    val shouldUpdateBranch = commitPops(w) && !lane.sync_valid && (lane.is_branch || predTakenNonBranch)

    when(shouldUpdateBranch) {
      bpuUpdateWire.valid        := true.B
      bpuUpdateWire.pc           := lane.pc
      bpuUpdateWire.target       := lane.bpu_actual_target
      bpuUpdateWire.taken        := lane.bpu_actual_taken
      bpuUpdateWire.branch_kind  := lane.bpu_branch_kind
      bpuUpdateWire.pht_index    := lane.bpu_pht_index
      bpuUpdateWire.ghr_snapshot := lane.bpu_ghr_snapshot
      bpuUpdateWire.provider     := lane.bpu_provider
      bpuUpdateWire.alt_taken    := lane.bpu_alt_taken
      bpuUpdateWire.pred_taken   := lane.bpu_pred_taken
      bpuUpdateWire.mispredict   := lane.flush_pipeline
      bpuUpdateWire.preserve_spec := committedEarlyRedirect &&
        lane.bpu_branch_kind === BpuBranchKind.BRANCH
    }
  }

  bpuUpdate.out := bpuUpdateWire

  for (w <- 0 until p(CommitWidth)) {
    val lane = commitInfo(w)

    committedRedirect.out.lanes(w).valid  := commitPops(w) && lane.flush_pipeline && !lane.sync_valid
    committedRedirect.out.lanes(w).target := lane.flush_target

    committedSync.out.lanes(w).valid  := commitPops(w) && lane.sync_valid
    committedSync.out.lanes(w).kind   := lane.sync_kind
    committedSync.out.lanes(w).target := lane.flush_target
    committedSync.out.lanes(w).pc     := lane.pc
  }

  debug.out.commit_count   := commitCount
  debug.out.branch_commit  := PopCount(
    (0 until p(CommitWidth)).map(w => commitPops(w) && commitInfo(w).is_branch && !commitInfo(w).sync_valid)
  )
  debug.out.bpu_mispredict := (0 until p(CommitWidth))
    .map(w => {
      val lane = commitInfo(w)
      commitPops(w) && !lane.sync_valid && (lane.is_branch || (!lane.is_branch && lane.bpu_pred_taken)) && lane.flush_pipeline
    })
    .reduce(_ || _)

  private val bpuMissBtb = (0 until p(CommitWidth)).map { w =>
    val lane = commitInfo(w)
    commitPops(w) && !lane.sync_valid && lane.flush_pipeline && lane.is_branch &&
      lane.bpu_actual_taken && !lane.bpu_btb_hit
  }
  private val bpuMissDirection = (0 until p(CommitWidth)).map { w =>
    val lane = commitInfo(w)
    commitPops(w) && !lane.sync_valid && lane.flush_pipeline && lane.is_branch &&
      lane.bpu_btb_hit && lane.bpu_branch_kind === BpuBranchKind.BRANCH &&
      lane.bpu_pred_taken =/= lane.bpu_actual_taken
  }
  private val bpuMissTarget = (0 until p(CommitWidth)).map { w =>
    val lane = commitInfo(w)
    commitPops(w) && !lane.sync_valid && lane.flush_pipeline && lane.is_branch &&
      lane.bpu_btb_hit && lane.bpu_pred_taken && lane.bpu_actual_taken &&
      lane.bpu_pred_target =/= lane.bpu_actual_target
  }
  private val bpuMissRasTarget = (0 until p(CommitWidth)).map { w =>
    val lane = commitInfo(w)
    bpuMissTarget(w) &&
      (lane.bpu_branch_kind === BpuBranchKind.RET || lane.bpu_branch_kind === BpuBranchKind.CALL_RET)
  }
  private val bpuMissBtbTarget = (0 until p(CommitWidth)).map { w =>
    bpuMissTarget(w) && !bpuMissRasTarget(w)
  }
  private val bpuMissFalseHit = (0 until p(CommitWidth)).map { w =>
    val lane = commitInfo(w)
    commitPops(w) && !lane.sync_valid && lane.flush_pipeline && !lane.is_branch && lane.bpu_pred_taken
  }
  private val bpuMissOther = (0 until p(CommitWidth)).map { w =>
    val classified = bpuMissBtb(w) || bpuMissDirection(w) || bpuMissBtbTarget(w) ||
      bpuMissRasTarget(w) || bpuMissFalseHit(w)
    val lane = commitInfo(w)
    commitPops(w) && !lane.sync_valid && lane.flush_pipeline &&
      (lane.is_branch || lane.bpu_pred_taken) && !classified
  }

  debug.out.bpu_miss_btb        := bpuMissBtb.reduce(_ || _)
  debug.out.bpu_miss_direction  := bpuMissDirection.reduce(_ || _)
  debug.out.bpu_miss_btb_target := bpuMissBtbTarget.reduce(_ || _)
  debug.out.bpu_miss_ras_target := bpuMissRasTarget.reduce(_ || _)
  debug.out.bpu_miss_false_hit  := bpuMissFalseHit.reduce(_ || _)
  debug.out.bpu_miss_other      := bpuMissOther.reduce(_ || _)
  debug.out.empty          := count === 0.U

  private val headEntry = buffer(head)
  private val headValid = count =/= 0.U && headEntry.valid

  debug.out.head_not_ready := headValid && !headEntry.ready
  debug.out.head_fu_type   := Mux(headValid, headEntry.fu_type, 0.U)

  for (w <- 0 until p(CommitWidth)) {
    debug.out.instret(w)  := commitPops(w) && !commitInfo(w).sync_valid
    debug.out.pc(w)       := commitInfo(w).pc
    debug.out.instr(w)    := commitInfo(w).instr
    debug.out.reg_we(w)   := rdWrite.out.lanes(w).valid
    debug.out.reg_addr(w) := rdWrite.out.lanes(w).bits.addr
    debug.out.reg_data(w) := rdWrite.out.lanes(w).bits.data
  }
}
