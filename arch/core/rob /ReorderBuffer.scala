package arch.core.rob

import arch.configs._
import chisel3._
import chisel3.util.{ log2Ceil, PopCount, MuxCase, PriorityEncoder, UIntToOH, Mux1H }

class RobEnqIO(implicit p: Parameters) extends Bundle {
  val valid            = Input(Bool())
  val ready            = Output(Bool())
  val pc               = Input(UInt(p(XLen).W))
  val instr            = Input(UInt(p(ILen).W))
  val rd               = Input(UInt(log2Ceil(p(NumArchRegs)).W))
  val pd               = Input(UInt(log2Ceil(p(NumPhyRegs)).W))
  val old_pd           = Input(UInt(log2Ceil(p(NumPhyRegs)).W))
  val is_branch        = Input(Bool())
  val is_store         = Input(Bool())
  val commit_barrier   = Input(Bool())
  val bpu_pred_taken   = Input(Bool())
  val bpu_pred_target  = Input(UInt(p(XLen).W))
  val bpu_pht_index    = Input(UInt(p(GShareGhrWidth).W))
  val bpu_ghr_snapshot = Input(UInt(p(GShareGhrWidth).W))
  val rob_tag          = Output(UInt(p(RobTagWidth).W))
  val sq_idx           = Input(UInt(log2Ceil(p(StoreBufferSize)).W))
}

class RobWbIO(implicit p: Parameters) extends Bundle {
  val valid         = Input(Bool())
  val rob_tag       = Input(UInt(p(RobTagWidth).W))
  val data          = Input(UInt(p(XLen).W))
  val is_bru        = Input(Bool())
  val actual_taken  = Input(Bool())
  val actual_target = Input(UInt(p(XLen).W))
  val trap_req      = Input(Bool())
  val trap_target   = Input(UInt(p(XLen).W))
  val trap_ret      = Input(Bool())
  val trap_ret_tgt  = Input(UInt(p(XLen).W))
}

class RobCommitIO(implicit p: Parameters) extends Bundle {
  val valid             = Output(Bool())
  val pop               = Input(Bool())
  val pc                = Output(UInt(p(XLen).W))
  val instr             = Output(UInt(p(ILen).W))
  val rd                = Output(UInt(log2Ceil(p(NumArchRegs)).W))
  val data              = Output(UInt(p(XLen).W))
  val pd                = Output(UInt(log2Ceil(p(NumPhyRegs)).W))
  val old_pd            = Output(UInt(log2Ceil(p(NumPhyRegs)).W))
  val flush_pipeline    = Output(Bool())
  val flush_target      = Output(UInt(p(XLen).W))
  val is_branch         = Output(Bool())
  val is_store          = Output(Bool())
  val commit_barrier    = Output(Bool())
  val bpu_pred_taken    = Output(Bool())
  val bpu_pred_target   = Output(UInt(p(XLen).W))
  val bpu_actual_taken  = Output(Bool())
  val bpu_actual_target = Output(UInt(p(XLen).W))
  val bpu_pht_index     = Output(UInt(p(GShareGhrWidth).W))
  val bpu_ghr_snapshot  = Output(UInt(p(GShareGhrWidth).W))
  val sq_idx            = Output(UInt(log2Ceil(p(StoreBufferSize)).W))
}

class RobBypassIO(implicit p: Parameters) extends Bundle {
  val valid   = Output(Bool())
  val data    = Output(UInt(p(XLen).W))
  val pending = Output(Bool())
}

class RobEntry(implicit p: Parameters) extends Bundle {
  val valid          = Bool()
  val ready          = Bool()
  val pc             = UInt(p(XLen).W)
  val instr          = UInt(p(ILen).W)
  val rd             = UInt(log2Ceil(p(NumArchRegs)).W)
  val data           = UInt(p(XLen).W)
  val pd             = UInt(log2Ceil(p(NumPhyRegs)).W)
  val old_pd         = UInt(log2Ceil(p(NumPhyRegs)).W)
  val is_branch      = Bool()
  val is_store       = Bool()
  val commit_barrier = Bool()
  val pred_taken     = Bool()
  val pred_target    = UInt(p(XLen).W)
  val pht_index      = UInt(p(GShareGhrWidth).W)
  val ghr_snapshot   = UInt(p(GShareGhrWidth).W)
  val actual_taken   = Bool()
  val actual_target  = UInt(p(XLen).W)
  val flush_pipeline = Bool()
  val flush_target   = UInt(p(XLen).W)
  val sq_idx         = UInt(log2Ceil(p(StoreBufferSize)).W)
}

class ReorderBuffer(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_rob"

  private val CntW = log2Ceil(p(RobSize) + 1)

  val io = IO(new Bundle {
    val enq    = Vec(p(IssueWidth), new RobEnqIO)
    val wb     = Vec(p(FunctionalUnits).size, new RobWbIO)
    val commit = Vec(p(IssueWidth), new RobCommitIO)

    val read_rob_tag = Input(Vec(p(IssueWidth), UInt(p(RobTagWidth).W)))
    val read_pd      = Output(Vec(p(IssueWidth), UInt(log2Ceil(p(NumPhyRegs)).W)))

    val rs1_addr   = Input(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
    val rs1_bypass = Vec(p(IssueWidth), new RobBypassIO)
    val rs2_addr   = Input(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
    val rs2_bypass = Vec(p(IssueWidth), new RobBypassIO)

    val empty = Output(Bool())
    val flush = Input(Bool())
  })

  private def wrapAdd(x: UInt, y: UInt): UInt = {
    val sum = x +& y
    Mux(sum >= p(RobSize).U, sum - p(RobSize).U, sum)(p(RobTagWidth) - 1, 0)
  }

  private def indexFromNewest(distance: Int): UInt = {
    val sub = distance + 1
    Mux(tail >= sub.U, tail - sub.U, tail + p(RobSize).U - sub.U)(p(RobTagWidth) - 1, 0)
  }

  val buffer = RegInit(VecInit(Seq.fill(p(RobSize))(0.U.asTypeOf(new RobEntry))))
  val head   = RegInit(0.U(p(RobTagWidth).W))
  val tail   = RegInit(0.U(p(RobTagWidth).W))
  val count  = RegInit(0.U(CntW.W))

  io.empty := count === 0.U

  for (w <- 0 until p(IssueWidth))
    io.read_pd(w) := buffer(io.read_rob_tag(w)).pd

  for (i <- 0 until p(FunctionalUnits).size)
    when(io.wb(i).valid) {
      val idx = io.wb(i).rob_tag

      buffer(idx).ready := true.B
      buffer(idx).data  := io.wb(i).data

      val oldPredTaken  = buffer(idx).pred_taken
      val oldPredTarget = buffer(idx).pred_target
      val oldPc         = buffer(idx).pc

      val bruMispredict = io.wb(i).is_bru && (
        io.wb(i).actual_taken =/= oldPredTaken ||
          (io.wb(i).actual_taken && io.wb(i).actual_target =/= oldPredTarget)
      )

      val nonBruMispredict = !io.wb(i).is_bru && oldPredTaken
      val isMispredict     = bruMispredict || nonBruMispredict
      val redirectTarget   = Mux(io.wb(i).is_bru, io.wb(i).actual_target, oldPc + p(PCStep).U)

      when(io.wb(i).is_bru) {
        buffer(idx).actual_taken  := io.wb(i).actual_taken
        buffer(idx).actual_target := io.wb(i).actual_target
      }.elsewhen(nonBruMispredict) {
        buffer(idx).actual_taken  := false.B
        buffer(idx).actual_target := oldPc + p(PCStep).U
      }

      buffer(idx).flush_pipeline := isMispredict || io.wb(i).trap_req || io.wb(i).trap_ret
      buffer(idx).flush_target   := MuxCase(
        redirectTarget,
        Seq(
          io.wb(i).trap_req -> io.wb(i).trap_target,
          io.wb(i).trap_ret -> io.wb(i).trap_ret_tgt
        )
      )
    }

  val commitCanContinue = Wire(Vec(p(IssueWidth) + 1, Bool()))
  val commitBlocked     = Wire(Vec(p(IssueWidth) + 1, Bool()))
  val commitIdx         = Wire(Vec(p(IssueWidth), UInt(p(RobTagWidth).W)))
  val commitPops        = Wire(Vec(p(IssueWidth), Bool()))

  commitCanContinue(0) := true.B
  commitBlocked(0)     := false.B

  for (w <- 0 until p(IssueWidth)) {
    commitIdx(w) := wrapAdd(head, w.U)

    val entry       = buffer(commitIdx(w))
    val hasEntry    = count > w.U
    val committable =
      hasEntry && entry.valid && entry.ready && commitCanContinue(w) && !commitBlocked(w)

    io.commit(w).valid             := committable
    io.commit(w).pc                := entry.pc
    io.commit(w).instr             := entry.instr
    io.commit(w).rd                := entry.rd
    io.commit(w).data              := entry.data
    io.commit(w).pd                := entry.pd
    io.commit(w).old_pd            := entry.old_pd
    io.commit(w).flush_pipeline    := entry.flush_pipeline
    io.commit(w).flush_target      := entry.flush_target
    io.commit(w).is_branch         := entry.is_branch
    io.commit(w).is_store          := entry.is_store
    io.commit(w).commit_barrier    := entry.commit_barrier
    io.commit(w).bpu_pred_taken    := entry.pred_taken
    io.commit(w).bpu_pred_target   := entry.pred_target
    io.commit(w).bpu_actual_taken  := entry.actual_taken
    io.commit(w).bpu_actual_target := entry.actual_target
    io.commit(w).bpu_pht_index     := entry.pht_index
    io.commit(w).bpu_ghr_snapshot  := entry.ghr_snapshot
    io.commit(w).sq_idx            := entry.sq_idx

    commitPops(w) := io.commit(w).pop

    val stopYoungerCommit = entry.flush_pipeline || entry.commit_barrier

    commitCanContinue(w + 1) := committable
    commitBlocked(w + 1)     := commitBlocked(w) || (committable && stopYoungerCommit)
  }

  val commitCount               = PopCount(commitPops)
  val availableSlots            = p(RobSize).U(CntW.W) - count
  val availableSlotsAfterCommit = availableSlots + commitCount

  val enqFire   = Wire(Vec(p(IssueWidth), Bool()))
  val enqOffset = Wire(Vec(p(IssueWidth), UInt(p(RobTagWidth).W)))
  val enqIdx    = Wire(Vec(p(IssueWidth), UInt(p(RobTagWidth).W)))

  for (w <- 0 until p(IssueWidth)) {
    val olderFires = Wire(UInt(CntW.W))

    if (w == 0) {
      olderFires := 0.U
    } else {
      olderFires := PopCount((0 until w).map(i => enqFire(i)))
    }

    io.enq(w).ready := availableSlotsAfterCommit > olderFires
    enqFire(w)      := io.enq(w).valid && io.enq(w).ready
    enqOffset(w)    := olderFires(p(RobTagWidth) - 1, 0)
    enqIdx(w)       := wrapAdd(tail, enqOffset(w))

    io.enq(w).rob_tag := enqIdx(w)
  }

  val enqCount = PopCount(enqFire)

  for (w <- 0 until p(IssueWidth))
    when(commitPops(w)) {
      buffer(commitIdx(w)).valid := false.B
    }

  for (w <- 0 until p(IssueWidth))
    when(enqFire(w)) {
      val idx = enqIdx(w)

      buffer(idx).valid          := true.B
      buffer(idx).ready          := false.B
      buffer(idx).pc             := io.enq(w).pc
      buffer(idx).instr          := io.enq(w).instr
      buffer(idx).rd             := io.enq(w).rd
      buffer(idx).data           := 0.U
      buffer(idx).pd             := io.enq(w).pd
      buffer(idx).old_pd         := io.enq(w).old_pd
      buffer(idx).is_branch      := io.enq(w).is_branch
      buffer(idx).is_store       := io.enq(w).is_store
      buffer(idx).commit_barrier := io.enq(w).commit_barrier
      buffer(idx).pred_taken     := io.enq(w).bpu_pred_taken
      buffer(idx).pred_target    := io.enq(w).bpu_pred_target
      buffer(idx).pht_index      := io.enq(w).bpu_pht_index
      buffer(idx).ghr_snapshot   := io.enq(w).bpu_ghr_snapshot
      buffer(idx).actual_taken   := false.B
      buffer(idx).actual_target  := 0.U
      buffer(idx).flush_pipeline := false.B
      buffer(idx).flush_target   := 0.U
      buffer(idx).sq_idx         := io.enq(w).sq_idx
    }

  head  := wrapAdd(head, commitCount)
  tail  := wrapAdd(tail, enqCount)
  count := count + enqCount - commitCount

  when(io.flush) {
    head  := 0.U
    tail  := 0.U
    count := 0.U

    for (i <- 0 until p(RobSize))
      buffer(i).valid := false.B
  }

  def bypassNewest(rs: UInt): (Bool, UInt, Bool) = {
    val matchVec = Wire(Vec(p(RobSize), Bool()))
    val readyVec = Wire(Vec(p(RobSize), Bool()))
    val dataVec  = Wire(Vec(p(RobSize), UInt(p(XLen).W)))

    for (d <- 0 until p(RobSize)) {
      val idx = indexFromNewest(d)
      val e   = buffer(idx)

      matchVec(d) := e.valid && e.rd === rs && rs =/= 0.U
      readyVec(d) := matchVec(d) && e.ready
      dataVec(d)  := e.data
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
    val (rs1Valid, rs1Data, rs1Pending) = bypassNewest(io.rs1_addr(w))
    io.rs1_bypass(w).valid   := rs1Valid
    io.rs1_bypass(w).data    := rs1Data
    io.rs1_bypass(w).pending := rs1Pending

    val (rs2Valid, rs2Data, rs2Pending) = bypassNewest(io.rs2_addr(w))
    io.rs2_bypass(w).valid   := rs2Valid
    io.rs2_bypass(w).data    := rs2Data
    io.rs2_bypass(w).pending := rs2Pending
  }
}
