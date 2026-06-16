package arch.core.scheduler.impls.policy.scoreboard

import arch.configs._
import arch.core.fupool.{ FuReq, FuResp, FunctionalUnitType }
import arch.core.scheduler._
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.{ DecoupledIO, Mux1H, PriorityEncoder, UIntToOH, log2Ceil }

class ScoreboardEntry(implicit p: Parameters) extends Bundle {
  val valid = Bool()
  val op    = new FuReq

  val q1_ready = Bool()
  val q1_tag   = UInt(p(RobTagWidth).W)
  val v1       = UInt(p(XLen).W)

  val q2_ready = Bool()
  val q2_tag   = UInt(p(RobTagWidth).W)
  val v2       = UInt(p(XLen).W)

  val seq = UInt(64.W)
}

object ScoreboardSchedulerPolicy extends RegisteredNodeUtils[SchedulerPolicyImpl] {
  override def utils: SchedulerPolicyImpl = new SchedulerPolicyImpl {
    override def value: String = "scoreboard"

    override def elaborate(
      exception: SchedulerExceptionReq,
      dispatchReq: Int => DecoupledIO[FuReq],
      fuReq: Int => DecoupledIO[FuReq],
      fuDone: Int => DecoupledIO[FuResp]
    )(implicit p: Parameters): Unit = {
      val numEntries = math.max(p(NumFUs) * 2, p(IssueWidth) * 4)

      val fuTypes =
        p(FunctionalUnits).map(_.`type`.index.U(p(FuTypeWidth).W))

      def isLoad(fuType: UInt): Bool =
        fuType === FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD.index.U(p(FuTypeWidth).W)

      def isStore(fuType: UInt): Bool =
        fuType === FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST.index.U(p(FuTypeWidth).W)

      def defaultFuReqs(): Unit =
        for (i <- 0 until p(NumFUs)) {
          fuReq(i).valid := false.B
          fuReq(i).bits  := 0.U.asTypeOf(new FuReq)
        }

      def defaultDispatchReady(): Unit =
        for (w <- 0 until p(IssueWidth))
          dispatchReq(w).ready := false.B

      def defaultFuDoneReady(): Unit =
        for (i <- 0 until p(NumFUs))
          fuDone(i).ready := true.B

      def olderLaneAccepted(w: Int, accepted: Vec[Bool]): Bool =
        if (w == 0) true.B else !dispatchReq(w - 1).valid || accepted(w - 1)

      def fuTypeSupported(op: FuReq): Bool =
        VecInit(fuTypes.map(t => t === op.fu_type)).asUInt.orR

      def noOlderCandidate(candidate: Vec[Bool], e: Int, table: Vec[ScoreboardEntry]): Bool = {
        val older = Wire(Vec(numEntries, Bool()))

        for (j <- 0 until numEntries)
          older(j) := candidate(j) && table(j).seq < table(e).seq

        !older.asUInt.orR
      }

      def noOlderStore(table: Vec[ScoreboardEntry], e: Int, alreadyTaken: Vec[Bool]): Bool = {
        val olderStore = Wire(Vec(numEntries, Bool()))

        for (j <- 0 until numEntries)
          olderStore(j) := table(j).valid &&
            !alreadyTaken(j) &&
            isStore(table(j).op.fu_type) &&
            table(j).seq < table(e).seq

        !olderStore.asUInt.orR
      }

      defaultFuReqs()
      defaultDispatchReady()
      defaultFuDoneReady()

      val reg_pending_valid   = RegInit(VecInit(Seq.fill(p(NumArchRegs))(false.B)))
      val reg_pending_rob     = RegInit(VecInit(Seq.fill(p(NumArchRegs))(0.U(p(RobTagWidth).W))))
      val reg_completed_valid = RegInit(VecInit(Seq.fill(p(NumArchRegs))(false.B)))
      val reg_completed_data  = RegInit(VecInit(Seq.fill(p(NumArchRegs))(0.U(p(XLen).W))))
      val dispatch_seq        = RegInit(0.U(64.W))
      val entries             = RegInit(VecInit(Seq.fill(numEntries)(0.U.asTypeOf(new ScoreboardEntry))))

      val cdb_valid   = Wire(Vec(p(NumFUs), Bool()))
      val cdb_data    = Wire(Vec(p(NumFUs), UInt(p(XLen).W)))
      val cdb_rob_tag = Wire(Vec(p(NumFUs), UInt(p(RobTagWidth).W)))
      val cdb_rd      = Wire(Vec(p(NumFUs), UInt(log2Ceil(p(NumArchRegs)).W)))

      for (i <- 0 until p(NumFUs)) {
        cdb_valid(i)   := fuDone(i).fire
        cdb_data(i)    := fuDone(i).bits.result
        cdb_rob_tag(i) := fuDone(i).bits.rob_tag
        cdb_rd(i)      := fuDone(i).bits.rd
      }

      val base_pending_valid   = Wire(Vec(p(NumArchRegs), Bool()))
      val base_pending_rob     = Wire(Vec(p(NumArchRegs), UInt(p(RobTagWidth).W)))
      val base_completed_valid = Wire(Vec(p(NumArchRegs), Bool()))
      val base_completed_data  = Wire(Vec(p(NumArchRegs), UInt(p(XLen).W)))

      for (r <- 0 until p(NumArchRegs)) {
        val hits = Wire(Vec(p(NumFUs), Bool()))

        for (c <- 0 until p(NumFUs))
          hits(c) := cdb_valid(c) && reg_pending_valid(r) && reg_pending_rob(r) === cdb_rob_tag(
            c
          ) && cdb_rd(c) === r.U

        val hit = hits.asUInt.orR

        base_pending_valid(r)   := reg_pending_valid(r) && !hit
        base_pending_rob(r)     := reg_pending_rob(r)
        base_completed_valid(r) := Mux(hit, true.B, reg_completed_valid(r))
        base_completed_data(r)  := Mux(hit, Mux1H(hits, cdb_data), reg_completed_data(r))
      }

      base_pending_valid(0)   := false.B
      base_pending_rob(0)     := 0.U
      base_completed_valid(0) := false.B
      base_completed_data(0)  := 0.U

      val wakeup_entries = Wire(Vec(numEntries, new ScoreboardEntry))
      wakeup_entries := entries

      for (e <- 0 until numEntries) {
        when(entries(e).valid && !entries(e).q1_ready) {
          val hits = Wire(Vec(p(NumFUs), Bool()))

          for (c <- 0 until p(NumFUs))
            hits(c) := cdb_valid(c) && entries(e).q1_tag === cdb_rob_tag(c)

          when(hits.asUInt.orR) {
            wakeup_entries(e).q1_ready := true.B
            wakeup_entries(e).v1       := Mux1H(hits, cdb_data)
          }
        }

        when(entries(e).valid && !entries(e).q2_ready) {
          val hits = Wire(Vec(p(NumFUs), Bool()))

          for (c <- 0 until p(NumFUs))
            hits(c) := cdb_valid(c) && entries(e).q2_tag === cdb_rob_tag(c)

          when(hits.asUInt.orR) {
            wakeup_entries(e).q2_ready := true.B
            wakeup_entries(e).v2       := Mux1H(hits, cdb_data)
          }
        }
      }

      val issueTaken    = Wire(Vec(p(NumFUs) + 1, Vec(numEntries, Bool())))
      val issuedEntries = Wire(Vec(numEntries, new ScoreboardEntry))

      issuedEntries := wakeup_entries

      for (e <- 0 until numEntries)
        issueTaken(0)(e) := false.B

      for (f <- 0 until p(NumFUs)) {
        val candidate = Wire(Vec(numEntries, Bool()))
        val oldest    = Wire(Vec(numEntries, Bool()))

        for (e <- 0 until numEntries) {
          val entryReady =
            wakeup_entries(e).valid && wakeup_entries(e).q1_ready && wakeup_entries(e).q2_ready
          val typeMatch  = wakeup_entries(e).op.fu_type === fuTypes(f)
          val memSafe    =
            !isLoad(wakeup_entries(e).op.fu_type) || noOlderStore(wakeup_entries, e, issueTaken(f))

          candidate(e) := entryReady && typeMatch && memSafe && !issueTaken(f)(e)
        }

        for (e <- 0 until numEntries)
          oldest(e) := candidate(e) && noOlderCandidate(candidate, e, wakeup_entries)

        val selValid = oldest.asUInt.orR
        val selIdx   = PriorityEncoder(oldest)
        val selOH    = UIntToOH(selIdx, numEntries)
        val selEntry = Mux1H((0 until numEntries).map(e => selOH(e) -> wakeup_entries(e)))

        fuReq(f).valid         := selValid
        fuReq(f).bits          := selEntry.op
        fuReq(f).bits.fu_id    := f.U
        fuReq(f).bits.rs1_data := selEntry.v1
        fuReq(f).bits.rs2_data := selEntry.v2

        issueTaken(f + 1) := issueTaken(f)

        when(selValid && fuReq(f).ready) {
          issuedEntries(selIdx).valid := false.B
          issueTaken(f + 1)(selIdx)   := true.B
        }
      }

      val dispatchedEntries = Wire(Vec(numEntries, new ScoreboardEntry))
      dispatchedEntries := issuedEntries

      val temp_pending_valid   = Wire(Vec(p(IssueWidth) + 1, Vec(p(NumArchRegs), Bool())))
      val temp_pending_rob     = Wire(
        Vec(p(IssueWidth) + 1, Vec(p(NumArchRegs), UInt(p(RobTagWidth).W)))
      )
      val temp_completed_valid = Wire(Vec(p(IssueWidth) + 1, Vec(p(NumArchRegs), Bool())))
      val temp_completed_data  = Wire(Vec(p(IssueWidth) + 1, Vec(p(NumArchRegs), UInt(p(XLen).W))))
      val temp_entry_free      = Wire(Vec(p(IssueWidth) + 1, Vec(numEntries, Bool())))
      val temp_seq             = Wire(Vec(p(IssueWidth) + 1, UInt(64.W)))
      val accepted             = Wire(Vec(p(IssueWidth), Bool()))

      temp_pending_valid(0)   := base_pending_valid
      temp_pending_rob(0)     := base_pending_rob
      temp_completed_valid(0) := base_completed_valid
      temp_completed_data(0)  := base_completed_data
      temp_seq(0)             := dispatch_seq

      for (e <- 0 until numEntries)
        temp_entry_free(0)(e) := !issuedEntries(e).valid

      for (w <- 0 until p(IssueWidth)) {
        val dis = dispatchReq(w)
        val op  = dis.bits

        val freeMask = Wire(Vec(numEntries, Bool()))

        for (e <- 0 until numEntries)
          freeMask(e) := temp_entry_free(w)(e)

        val targetEntry = PriorityEncoder(freeMask)
        val hasFree     = freeMask.asUInt.orR
        val prevOk      = olderLaneAccepted(w, accepted)
        val supported   = fuTypeSupported(op)
        val writesRd    = op.rd_write && op.rd =/= 0.U
        val canAccept   = prevOk && hasFree && supported

        dis.ready   := canAccept
        accepted(w) := dis.valid && canAccept

        temp_pending_valid(w + 1)   := temp_pending_valid(w)
        temp_pending_rob(w + 1)     := temp_pending_rob(w)
        temp_completed_valid(w + 1) := temp_completed_valid(w)
        temp_completed_data(w + 1)  := temp_completed_data(w)
        temp_entry_free(w + 1)      := temp_entry_free(w)
        temp_seq(w + 1)             := temp_seq(w)

        when(accepted(w)) {
          temp_entry_free(w + 1)(targetEntry) := false.B

          when(writesRd) {
            temp_pending_valid(w + 1)(op.rd)   := true.B
            temp_pending_rob(w + 1)(op.rd)     := op.rob_tag
            temp_completed_valid(w + 1)(op.rd) := false.B
            temp_completed_data(w + 1)(op.rd)  := 0.U
          }

          val entryOp = Wire(new FuReq)
          entryOp       := op
          entryOp.fu_id := 0.U

          dispatchedEntries(targetEntry).valid := true.B
          dispatchedEntries(targetEntry).op    := entryOp
          dispatchedEntries(targetEntry).seq   := temp_seq(w)

          temp_seq(w + 1) := temp_seq(w) + 1.U

          val r1Used      = op.rs1_read
          val r1Pending   = r1Used && temp_pending_valid(w)(op.rs1)
          val r1Completed = r1Used && !r1Pending && temp_completed_valid(w)(op.rs1)
          val r1RobTag    = temp_pending_rob(w)(op.rs1)
          val r1Hits      = Wire(Vec(p(NumFUs), Bool()))

          for (c <- 0 until p(NumFUs))
            r1Hits(c) := cdb_valid(c) && r1RobTag === cdb_rob_tag(c)

          val r1CdbValid = r1Pending && r1Hits.asUInt.orR
          val r1CdbData  = Mux1H(r1Hits, cdb_data)

          dispatchedEntries(targetEntry).q1_ready := !r1Pending || r1CdbValid
          dispatchedEntries(targetEntry).q1_tag   := r1RobTag
          dispatchedEntries(targetEntry).v1       := Mux(
            r1CdbValid,
            r1CdbData,
            Mux(r1Completed, temp_completed_data(w)(op.rs1), op.rs1_data)
          )

          val r2Used      = op.rs2_read
          val r2Pending   = r2Used && temp_pending_valid(w)(op.rs2)
          val r2Completed = r2Used && !r2Pending && temp_completed_valid(w)(op.rs2)
          val r2RobTag    = temp_pending_rob(w)(op.rs2)
          val r2Hits      = Wire(Vec(p(NumFUs), Bool()))

          for (c <- 0 until p(NumFUs))
            r2Hits(c) := cdb_valid(c) && r2RobTag === cdb_rob_tag(c)

          val r2CdbValid = r2Pending && r2Hits.asUInt.orR
          val r2CdbData  = Mux1H(r2Hits, cdb_data)

          dispatchedEntries(targetEntry).q2_ready := !r2Pending || r2CdbValid
          dispatchedEntries(targetEntry).q2_tag   := r2RobTag
          dispatchedEntries(targetEntry).v2       := Mux(
            r2CdbValid,
            r2CdbData,
            Mux(r2Completed, temp_completed_data(w)(op.rs2), op.rs2_data)
          )
        }
      }

      when(exception.flush) {
        for (r <- 0 until p(NumArchRegs)) {
          reg_pending_valid(r)   := false.B
          reg_pending_rob(r)     := 0.U
          reg_completed_valid(r) := false.B
          reg_completed_data(r)  := 0.U
        }

        for (e <- 0 until numEntries)
          entries(e).valid := false.B

        dispatch_seq := 0.U
      }.otherwise {
        reg_pending_valid   := temp_pending_valid(p(IssueWidth))
        reg_pending_rob     := temp_pending_rob(p(IssueWidth))
        reg_completed_valid := temp_completed_valid(p(IssueWidth))
        reg_completed_data  := temp_completed_data(p(IssueWidth))
        entries             := dispatchedEntries
        dispatch_seq        := temp_seq(p(IssueWidth))
      }
    }
  }

  override def registry: NodeDimensionRegistry[SchedulerPolicyImpl] =
    SchedulerPolicyFactory
}
