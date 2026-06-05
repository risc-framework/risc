package arch.core.rob

import vutils.graph.{ Node, NodeType }
import arch.configs._
import chisel3._
import chisel3.util.{ Mux1H, PopCount, PriorityEncoder, UIntToOH, log2Ceil }

class RobIO(implicit p: Parameters) extends Bundle {
  val flush     = new RobFlushIO
  val exception = new RobExceptionIO
  val enq       = new RobEnqPortIO
  val wb        = new RobWbPortIO
  val bru       = new RobBruPortIO
  val trap      = new RobTrapPortIO
  val commit    = new RobCommitPortIO
  val bypass    = new RobBypassIO
  val ctrl      = new RobCtrlIO
}

class Rob(implicit p: Parameters) extends Node(new RobIO) {
  override def nodeType: NodeType  = RobMeta.Type
  override def desiredName: String = "rob"

  private val CntW = log2Ceil(p(RobSize) + 1)

  private val buffer = RegInit(VecInit(Seq.fill(p(RobSize))(0.U.asTypeOf(new RobEntry))))
  private val head   = RegInit(0.U(p(RobTagWidth).W))
  private val tail   = RegInit(0.U(p(RobTagWidth).W))
  private val count  = RegInit(0.U(CntW.W))

  private def wrapAdd(x: UInt, y: UInt): UInt = {
    val sum = x +& y
    Mux(sum >= p(RobSize).U, sum - p(RobSize).U, sum)(p(RobTagWidth) - 1, 0)
  }

  private def indexFromNewest(distance: Int): UInt = {
    val sub = distance + 1
    Mux(tail >= sub.U, tail - sub.U, tail + p(RobSize).U - sub.U)(p(RobTagWidth) - 1, 0)
  }

  io.ctrl.empty := count === 0.U

  for (i <- 0 until p(NumFUs))
    when(io.wb.ports(i).valid) {
      val idx              = io.wb.ports(i).rob_tag
      val oldPc            = buffer(idx).pc
      val nonBruMispredict = !buffer(idx).is_branch && buffer(idx).pred_taken
      val nonBruRedirect   = oldPc + p(PCStep).U(p(XLen).W)

      buffer(idx).ready := true.B
      buffer(idx).data  := io.wb.ports(i).data

      when(nonBruMispredict) {
        buffer(idx).actual_taken   := false.B
        buffer(idx).actual_target  := nonBruRedirect
        buffer(idx).flush_pipeline := true.B
        buffer(idx).flush_target   := nonBruRedirect
      }
    }

  for (i <- 0 until io.bru.ports.length)
    when(io.bru.ports(i).resolved.valid) {
      val resolved      = io.bru.ports(i).resolved.bits
      val idx           = resolved.rob_tag
      val oldPredTaken  = buffer(idx).pred_taken
      val oldPredTarget = buffer(idx).pred_target
      val bruMispredict =
        resolved.taken =/= oldPredTaken || (resolved.taken && resolved.target =/= oldPredTarget)

      buffer(idx).actual_taken  := resolved.taken
      buffer(idx).actual_target := resolved.target

      when(bruMispredict) {
        buffer(idx).flush_pipeline := true.B
        buffer(idx).flush_target   := resolved.target
      }
    }

  for (i <- 0 until p(NumFUs))
    when(
      io.trap.ports(i).valid && (io.trap.ports(i).bits.trap_req || io.trap.ports(i).bits.trap_ret)
    ) {
      val trap = io.trap.ports(i).bits
      val idx  = trap.rob_tag

      buffer(idx).flush_pipeline := true.B
      buffer(idx).flush_target   := Mux(trap.trap_req, trap.trap_target, trap.trap_ret_tgt)
    }

  private val commitCanContinue = Wire(Vec(p(IssueWidth) + 1, Bool()))
  private val commitBlocked     = Wire(Vec(p(IssueWidth) + 1, Bool()))
  private val commitIdx         = Wire(Vec(p(IssueWidth), UInt(p(RobTagWidth).W)))
  private val commitPops        = Wire(Vec(p(IssueWidth), Bool()))

  commitCanContinue(0) := true.B
  commitBlocked(0)     := false.B

  for (w <- 0 until p(IssueWidth)) {
    commitIdx(w) := wrapAdd(head, w.U)

    val entry       = buffer(commitIdx(w))
    val hasEntry    = count > w.U
    val committable =
      hasEntry && entry.valid && entry.ready && commitCanContinue(w) && !commitBlocked(w)

    io.commit.lanes(w).valid             := committable
    io.commit.lanes(w).pc                := entry.pc
    io.commit.lanes(w).instr             := entry.instr
    io.commit.lanes(w).rd                := entry.rd
    io.commit.lanes(w).rd_write          := entry.rd_write
    io.commit.lanes(w).data              := entry.data
    io.commit.lanes(w).flush_pipeline    := entry.flush_pipeline
    io.commit.lanes(w).flush_target      := entry.flush_target
    io.commit.lanes(w).is_branch         := entry.is_branch
    io.commit.lanes(w).is_store          := entry.is_store
    io.commit.lanes(w).commit_barrier    := entry.commit_barrier
    io.commit.lanes(w).bpu_pred_taken    := entry.pred_taken
    io.commit.lanes(w).bpu_pred_target   := entry.pred_target
    io.commit.lanes(w).bpu_actual_taken  := entry.actual_taken
    io.commit.lanes(w).bpu_actual_target := entry.actual_target
    io.commit.lanes(w).bpu_pht_index     := entry.pht_index
    io.commit.lanes(w).bpu_ghr_snapshot  := entry.ghr_snapshot
    io.commit.lanes(w).sq_idx            := entry.sq_idx

    commitPops(w) := io.commit.lanes(w).valid && io.commit.lanes(w).pop

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

    io.enq.lanes(w).req.ready := availableSlotsAfterCommit > olderFires
    enqFire(w)                := io.enq.lanes(w).req.valid && io.enq.lanes(w).req.ready
    enqOffset(w)              := olderFires(p(RobTagWidth) - 1, 0)
    enqIdx(w)                 := wrapAdd(tail, enqOffset(w))

    io.enq.lanes(w).rob_tag := enqIdx(w)
  }

  private val enqCount = PopCount(enqFire)

  for (w <- 0 until p(IssueWidth))
    when(commitPops(w)) {
      buffer(commitIdx(w)).valid := false.B
    }

  for (w <- 0 until p(IssueWidth))
    when(enqFire(w)) {
      val idx = enqIdx(w)
      val pkt = io.enq.lanes(w).req.bits
      val dec = pkt.decoded

      buffer(idx).valid          := true.B
      buffer(idx).ready          := false.B
      buffer(idx).pc             := dec.pc
      buffer(idx).instr          := dec.instr
      buffer(idx).rd             := dec.rd
      buffer(idx).rd_write       := dec.rd_write
      buffer(idx).data           := 0.U
      buffer(idx).is_branch      := dec.isBru
      buffer(idx).is_store       := dec.isStore
      buffer(idx).commit_barrier := dec.commit_barrier
      buffer(idx).pred_taken     := dec.bpu_pred_taken
      buffer(idx).pred_target    := dec.bpu_pred_target
      buffer(idx).pht_index      := dec.bpu_pht_index
      buffer(idx).ghr_snapshot   := dec.bpu_ghr_snapshot
      buffer(idx).actual_taken   := false.B
      buffer(idx).actual_target  := 0.U
      buffer(idx).flush_pipeline := false.B
      buffer(idx).flush_target   := 0.U
      buffer(idx).sq_idx         := pkt.sq_idx
    }

  head  := wrapAdd(head, commitCount)
  tail  := wrapAdd(tail, enqCount)
  count := count + enqCount - commitCount

  when(io.exception.flush) {
    head  := 0.U
    tail  := 0.U
    count := 0.U

    for (i <- 0 until p(RobSize))
      buffer(i).valid := false.B
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

  for (w <- 0 until p(IssueWidth)) {
    val (rs1Valid, rs1Data, rs1Pending) = bypassNewest(io.bypass.rs1_addr(w))
    val (rs2Valid, rs2Data, rs2Pending) = bypassNewest(io.bypass.rs2_addr(w))

    io.bypass.rs1_bypass(w).valid   := rs1Valid
    io.bypass.rs1_bypass(w).data    := rs1Data
    io.bypass.rs1_bypass(w).pending := rs1Pending

    io.bypass.rs2_bypass(w).valid   := rs2Valid
    io.bypass.rs2_bypass(w).data    := rs2Data
    io.bypass.rs2_bypass(w).pending := rs2Pending
  }

  io.flush.flushes := io.commit.lanes.map(_.flush_pipeline).zip(io.commit.lanes.map(_.pop)).map {
    case (f, p) => f && p
  }
  io.flush.targets := io.commit.lanes.map(_.flush_target)
}
