package arch.core.rob

import arch.configs._
import arch.core.bpu.BpuUpdate
import arch.core.bru.BruResolveBundle
import arch.core.dispatch.{ DispatchRobPacket, DispatchRobResp }
import arch.core.flush.FlushRobReq
import arch.core.fupool.FuResp
import arch.core.regfile.RegfileWrite
import vutils.graph.{ NodeConfig, NodeSelector, Node }
import chisel3._
import chisel3.util.{ Mux1H, PopCount, PriorityEncoder, UIntToOH, log2Ceil }

class Rob(implicit p: Parameters) extends Node[Parameters]("rob") {
  override protected def cfg: NodeConfig = NodeConfig(
    selector = NodeSelector(
      RobDims.STORAGE -> p(RobStorageType)
    )
  )

  val dispatchReq   = inDVec[DispatchRobPacket](p => p(IssueWidth))
  val dispatchResp  = outVec[DispatchRobResp](p => p(IssueWidth))
  val fuDone        = inDVec[FuResp](p => p(NumFUs))
  val bruResolved   = inVVec[BruResolveBundle](p => p(NumBRUs))
  val regfileWrite  = outVVec[RegfileWrite](p => p(CommitWidth))
  val sbCommit      = outVVec[RobSbCommit](p => p(CommitWidth))
  val bpuUpdate     = out[BpuUpdate]
  val flush         = out[FlushRobReq]
  val exceptionReq  = in[RobExceptionReq]
  val exceptionResp = out[RobExceptionResp]
  val debug         = out[RobDebugInfo]

  private val CntW = log2Ceil(p(RobSize) + 1)

  private val buffer = RegInit(VecInit(Seq.fill(p(RobSize))(0.U.asTypeOf(new RobEntry))))
  private val head   = RegInit(0.U(p(RobTagWidth).W))
  private val tail   = RegInit(0.U(p(RobTagWidth).W))
  private val count  = RegInit(0.U(CntW.W))

  for (i <- 0 until p(NumFUs))
    fuDone.in.lanes(i).ready := true.B

  private def wrapAdd(x: UInt, y: UInt): UInt = {
    val sum = x +& y
    Mux(sum >= p(RobSize).U, sum - p(RobSize).U, sum)(p(RobTagWidth) - 1, 0)
  }

  private def indexFromNewest(distance: Int): UInt = {
    val sub = distance + 1
    Mux(tail >= sub.U, tail - sub.U, tail + p(RobSize).U - sub.U)(p(RobTagWidth) - 1, 0)
  }

  private def bypassNewest(rs: UInt): (Bool, UInt, Bool) = {
    val matchVec = Wire(Vec(p(RobSize), Bool()))
    val readyVec = Wire(Vec(p(RobSize), Bool()))
    val dataVec  = Wire(Vec(p(RobSize), UInt(p(XLen).W)))

    for (d <- 0 until p(RobSize)) {
      val idx   = indexFromNewest(d)
      val entry = buffer(idx)

      matchVec(d) := entry.valid && entry.rd_write && entry.rd === rs
      readyVec(d) := matchVec(d) && entry.ready
      dataVec(d)  := entry.data
    }

    val anyMatch    = matchVec.asUInt.orR
    val newest      = PriorityEncoder(matchVec)
    val newestOH    = UIntToOH(newest, p(RobSize))
    val newestReady = anyMatch && Mux1H(newestOH, readyVec)
    val newestData  = Mux(anyMatch, Mux1H(newestOH, dataVec), 0.U(p(XLen).W))
    val pending     = anyMatch && !newestReady

    (newestReady, newestData, pending)
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
        buffer(idx).flush_pipeline         := true.B
        buffer(idx).flush_target           := done.trap_target
        buffer(idx).sync_valid             := true.B
        buffer(idx).sync_kind              := done.trap_kind
        buffer(idx).sync_requires_csr_idle := true.B
      }
    }

  for (i <- 0 until p(NumBRUs))
    when(bruResolved.in.lanes(i).valid) {
      val resolved      = bruResolved.in.lanes(i).bits
      val idx           = resolved.rob_tag
      val oldPredTaken  = buffer(idx).pred_taken
      val oldPredTarget = buffer(idx).pred_target
      val actualTarget  = Mux(resolved.taken, resolved.target, resolved.fallthrough)
      val bruMispredict = resolved.taken =/= oldPredTaken || actualTarget =/= oldPredTarget

      buffer(idx).actual_taken  := resolved.taken
      buffer(idx).actual_target := actualTarget

      when(bruMispredict) {
        buffer(idx).flush_pipeline := true.B
        buffer(idx).flush_target   := actualTarget
      }
    }

  private val commitCanContinue = Wire(Vec(p(CommitWidth) + 1, Bool()))
  private val commitBlocked     = Wire(Vec(p(CommitWidth) + 1, Bool()))
  private val commitIdx         = Wire(Vec(p(CommitWidth), UInt(p(RobTagWidth).W)))
  private val commitInfo        = Wire(Vec(p(CommitWidth), new RobCommitInfo))
  private val commitPops        = Wire(Vec(p(CommitWidth), Bool()))

  commitCanContinue(0) := true.B
  commitBlocked(0)     := false.B

  for (w <- 0 until p(CommitWidth)) {
    commitIdx(w) := wrapAdd(head, w.U)

    val entry       = buffer(commitIdx(w))
    val hasEntry    = count > w.U
    val committable =
      hasEntry && entry.valid && entry.ready && commitCanContinue(w) && !commitBlocked(w)

    commitInfo(w).valid                  := committable
    commitInfo(w).pop                    := committable
    commitInfo(w).pc                     := entry.pc
    commitInfo(w).instr                  := entry.instr
    commitInfo(w).rd                     := entry.rd
    commitInfo(w).rd_write               := entry.rd_write
    commitInfo(w).data                   := entry.data
    commitInfo(w).flush_pipeline         := entry.flush_pipeline
    commitInfo(w).flush_target           := entry.flush_target
    commitInfo(w).is_branch              := entry.is_branch
    commitInfo(w).is_store               := entry.is_store
    commitInfo(w).commit_barrier         := entry.commit_barrier
    commitInfo(w).bpu_pred_taken         := entry.pred_taken
    commitInfo(w).bpu_pred_target        := entry.pred_target
    commitInfo(w).bpu_actual_taken       := entry.actual_taken
    commitInfo(w).bpu_actual_target      := entry.actual_target
    commitInfo(w).bpu_pht_index          := entry.pht_index
    commitInfo(w).bpu_ghr_snapshot       := entry.ghr_snapshot
    commitInfo(w).bpu_branch_kind        := entry.branch_kind
    commitInfo(w).sq_idx                 := entry.sq_idx
    commitInfo(w).sync_valid             := entry.sync_valid
    commitInfo(w).sync_kind              := entry.sync_kind
    commitInfo(w).sync_requires_csr_idle := entry.sync_requires_csr_idle

    commitPops(w) := commitInfo(w).pop

    val stopYoungerCommit = entry.flush_pipeline || entry.commit_barrier

    commitCanContinue(w + 1) := committable
    commitBlocked(w + 1)     := commitBlocked(w) || (committable && stopYoungerCommit)
  }

  private val commitCount               = PopCount(commitPops)
  private val availableSlots            = p(RobSize).U(CntW.W) - count
  private val availableSlotsAfterCommit = availableSlots + commitCount
  private val enqFire                   = Wire(Vec(p(IssueWidth), Bool()))
  private val enqOffset                 = Wire(Vec(p(IssueWidth), UInt(p(RobTagWidth).W)))
  private val enqIdx                    = Wire(Vec(p(IssueWidth), UInt(p(RobTagWidth).W)))

  for (w <- 0 until p(IssueWidth)) {
    val olderFires = Wire(UInt(CntW.W))

    if (w == 0) {
      olderFires := 0.U
    } else {
      olderFires := PopCount((0 until w).map(i => enqFire(i)))
    }

    dispatchReq.in.lanes(w).ready := availableSlotsAfterCommit > olderFires
    enqFire(w)                    := dispatchReq.in.lanes(w).valid && dispatchReq.in.lanes(w).ready
    enqOffset(w)                  := olderFires(p(RobTagWidth) - 1, 0)
    enqIdx(w)                     := wrapAdd(tail, enqOffset(w))

    dispatchResp.out.lanes(w).rob_tag := enqIdx(w)
  }

  private val enqCount = PopCount(enqFire)

  for (w <- 0 until p(CommitWidth))
    when(commitPops(w)) {
      buffer(commitIdx(w)).valid := false.B
    }

  for (w <- 0 until p(IssueWidth))
    when(enqFire(w)) {
      val idx = enqIdx(w)
      val pkt = dispatchReq.in.lanes(w).bits
      val dec = pkt.decoded

      buffer(idx).valid                  := true.B
      buffer(idx).ready                  := false.B
      buffer(idx).pc                     := dec.pc
      buffer(idx).instr                  := dec.instr
      buffer(idx).rd                     := dec.rd
      buffer(idx).rd_write               := dec.rd_write
      buffer(idx).data                   := 0.U
      buffer(idx).fu_type                := dec.fu_type
      buffer(idx).is_branch              := dec.isBru
      buffer(idx).is_store               := dec.isStore
      buffer(idx).commit_barrier         := dec.commit_barrier
      buffer(idx).pred_taken             := dec.bpu_pred_taken
      buffer(idx).pred_target            := dec.bpu_pred_target
      buffer(idx).pht_index              := dec.bpu_pht_index
      buffer(idx).ghr_snapshot           := dec.bpu_ghr_snapshot
      buffer(idx).branch_kind            := dec.bpu_branch_kind
      buffer(idx).actual_taken           := false.B
      buffer(idx).actual_target          := 0.U
      buffer(idx).flush_pipeline         := false.B
      buffer(idx).flush_target           := 0.U
      buffer(idx).sq_idx                 := pkt.sq_idx
      buffer(idx).sync_valid             := false.B
      buffer(idx).sync_kind              := 0.U
      buffer(idx).sync_requires_csr_idle := false.B
    }

  head  := wrapAdd(head, commitCount)
  tail  := wrapAdd(tail, enqCount)
  count := count + enqCount - commitCount

  when(exceptionReq.in.flush) {
    head  := 0.U
    tail  := 0.U
    count := 0.U

    for (i <- 0 until p(RobSize))
      buffer(i).valid := false.B
  }

  for (w <- 0 until p(IssueWidth)) {
    val dec = dispatchReq.in.lanes(w).bits.decoded

    val (rs1Valid, rs1Data, rs1Pending) = bypassNewest(dec.rs1)
    val (rs2Valid, rs2Data, rs2Pending) = bypassNewest(dec.rs2)

    dispatchResp.out.lanes(w).rs1_bypass_valid   := rs1Valid
    dispatchResp.out.lanes(w).rs1_bypass_data    := rs1Data
    dispatchResp.out.lanes(w).rs1_bypass_pending := rs1Pending

    dispatchResp.out.lanes(w).rs2_bypass_valid   := rs2Valid
    dispatchResp.out.lanes(w).rs2_bypass_data    := rs2Data
    dispatchResp.out.lanes(w).rs2_bypass_pending := rs2Pending
  }

  for (w <- 0 until p(CommitWidth)) {
    val lane = commitInfo(w)

    regfileWrite.out.lanes(w).valid     := lane.pop && lane.rd_write && !lane.sync_valid
    regfileWrite.out.lanes(w).bits.addr := lane.rd
    regfileWrite.out.lanes(w).bits.data := lane.data

    sbCommit.out.lanes(w).valid         := lane.pop && !lane.sync_valid
    sbCommit.out.lanes(w).bits.is_store := lane.is_store
    sbCommit.out.lanes(w).bits.sq_idx   := lane.sq_idx
  }

  private val bpuUpdateWire = WireDefault(0.U.asTypeOf(new BpuUpdate))

  for (w <- 0 until p(CommitWidth)) {
    val lane               = commitInfo(w)
    val predTakenNonBranch = !lane.is_branch && lane.bpu_pred_taken
    val shouldUpdateBranch = lane.pop && !lane.sync_valid && (lane.is_branch || predTakenNonBranch)

    when(shouldUpdateBranch) {
      bpuUpdateWire.valid        := true.B
      bpuUpdateWire.pc           := lane.pc
      bpuUpdateWire.target       := lane.bpu_actual_target
      bpuUpdateWire.taken        := lane.bpu_actual_taken
      bpuUpdateWire.branch_kind  := lane.bpu_branch_kind
      bpuUpdateWire.pht_index    := lane.bpu_pht_index
      bpuUpdateWire.ghr_snapshot := lane.bpu_ghr_snapshot
      bpuUpdateWire.mispredict   := lane.flush_pipeline
    }
  }

  bpuUpdate.out := bpuUpdateWire

  for (w <- 0 until p(CommitWidth)) {
    val lane = commitInfo(w)

    flush.out.redirect_valid(w)  := lane.pop && lane.flush_pipeline && !lane.sync_valid
    flush.out.redirect_target(w) := lane.flush_target

    flush.out.sync(w).valid             := lane.pop && lane.sync_valid
    flush.out.sync(w).kind              := lane.sync_kind
    flush.out.sync(w).target            := lane.flush_target
    flush.out.sync(w).pc                := lane.pc
    flush.out.sync(w).requires_csr_idle := lane.sync_requires_csr_idle
  }

  exceptionResp.out.empty     := count === 0.U
  exceptionResp.out.commit_pc := commitInfo(0).pc

  debug.out.commit_count  := commitCount
  debug.out.branch_commit := PopCount(
    commitInfo.map(lane => lane.pop && lane.is_branch && !lane.sync_valid)
  )

  debug.out.bpu_mispredict := commitInfo
    .map(lane =>
      lane.pop &&
        !lane.sync_valid &&
        (lane.is_branch || (!lane.is_branch && lane.bpu_pred_taken)) &&
        lane.flush_pipeline
    )
    .reduce(_ || _)

  debug.out.empty := count === 0.U

  private val headEntry = buffer(head)
  private val headValid = count =/= 0.U && headEntry.valid

  debug.out.head_not_ready := headValid && !headEntry.ready
  debug.out.head_fu_type   := Mux(headValid, headEntry.fu_type, 0.U)

  for (w <- 0 until p(CommitWidth)) {
    debug.out.instret(w)  := commitInfo(w).pop && !commitInfo(w).sync_valid
    debug.out.pc(w)       := commitInfo(w).pc
    debug.out.instr(w)    := commitInfo(w).instr
    debug.out.reg_we(w)   := regfileWrite.out.lanes(w).valid
    debug.out.reg_addr(w) := regfileWrite.out.lanes(w).bits.addr
    debug.out.reg_data(w) := regfileWrite.out.lanes(w).bits.data
  }
}
