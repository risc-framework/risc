package arch.core.scheduler.impls.policy.scoreboard

import arch.configs._
import arch.core.fupool.{ FuReq, FuResp }
import arch.core.scheduler._
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.{ DecoupledIO, Mux1H, PriorityEncoder, UIntToOH }

object ScoreboardSchedulerPolicy extends RegisteredNodeUtils[SchedulerPolicyImpl] {
  override def utils: SchedulerPolicyImpl = new SchedulerPolicyImpl {
    override def value: String = "scoreboard"

    override def elaborate(
      flush: Bool,
      dispatched: Int => DecoupledIO[FuReq],
      fuReq: Int => DecoupledIO[FuReq],
      fuDone: Int => DecoupledIO[FuResp],
      debug: SchedulerDebugInfo
    )(implicit p: Parameters): Unit = {
      val fuTypes = p(FunctionalUnits).map(_.`type`.index.U(p(FuTypeWidth).W))

      def defaultFuReqs(): Unit =
        for (i <- 0 until p(NumFUs)) {
          fuReq(i).valid := false.B
          fuReq(i).bits  := 0.U.asTypeOf(new FuReq)
        }

      def defaultDispatchReady(): Unit =
        for (w <- 0 until p(IssueWidth))
          dispatched(w).ready := false.B

      def defaultFuDoneReady(): Unit =
        for (i <- 0 until p(NumFUs))
          fuDone(i).ready := true.B

      def fuMatchMask(op: FuReq, used: Vec[Bool]): Vec[Bool] = {
        val mask = Wire(Vec(p(NumFUs), Bool()))
        for (i <- 0 until p(NumFUs))
          mask(i) := !used(i) && fuReq(i).ready && fuTypes(i) === op.fu_type
        mask
      }

      def fuTypeMask(op: FuReq): Vec[Bool] = {
        val mask = Wire(Vec(p(NumFUs), Bool()))
        for (i <- 0 until p(NumFUs))
          mask(i) := fuTypes(i) === op.fu_type
        mask
      }

      def selectFu(op: FuReq, used: Vec[Bool]): (UInt, Bool) = {
        val mask = fuMatchMask(op, used)
        (PriorityEncoder(mask), mask.asUInt.orR)
      }

      def olderLaneAccepted(w: Int, accepted: Vec[Bool]): Bool =
        if (w == 0) true.B else !dispatched(w - 1).valid || accepted(w - 1)

      def cdbForward(rs: UInt): (Bool, UInt) = {
        val hits = Wire(Vec(p(NumFUs), Bool()))

        for (f <- 0 until p(NumFUs))
          hits(f) := fuDone(f).fire && rs =/= 0.U && fuDone(f).bits.rd === rs

        val valid = hits.asUInt.orR
        val data  = Mux(
          valid,
          Mux1H(hits, (0 until p(NumFUs)).map(f => fuDone(f).bits.result)),
          0.U
        )

        (valid, data)
      }

      val regPending = RegInit(0.U(p(NumArchRegs).W))

      defaultFuReqs()
      defaultDispatchReady()
      defaultFuDoneReady()

      val cdbWriteMasks = Wire(Vec(p(NumFUs), UInt(p(NumArchRegs).W)))

      for (f <- 0 until p(NumFUs))
        cdbWriteMasks(f) := Mux(
          fuDone(f).fire && fuDone(f).bits.rd =/= 0.U,
          UIntToOH(fuDone(f).bits.rd, p(NumArchRegs)),
          0.U
        )

      val cdbWriteMask = cdbWriteMasks.reduce(_ | _)

      val basePending       = regPending & ~cdbWriteMask
      val tempFuUsed        = Wire(Vec(p(IssueWidth) + 1, Vec(p(NumFUs), Bool())))
      val accepted          = Wire(Vec(p(IssueWidth), Bool()))
      val acceptedWriteMask = Wire(Vec(p(IssueWidth), UInt(p(NumArchRegs).W)))
      val rawWait           = Wire(Vec(p(IssueWidth), Bool()))
      val wawWait           = Wire(Vec(p(IssueWidth), Bool()))
      val fuBusy            = Wire(Vec(p(IssueWidth), Bool()))
      val olderBlock        = Wire(Vec(p(IssueWidth), Bool()))
      val noMatch           = Wire(Vec(p(IssueWidth), Bool()))

      for (f <- 0 until p(NumFUs))
        tempFuUsed(0)(f) := false.B

      for (w <- 0 until p(IssueWidth)) {
        val dis = dispatched(w)
        val op  = dis.bits

        val rs1Used  = op.rs1_read
        val rs2Used  = op.rs2_read
        val writesRd = op.rd_write

        val olderRs1Haz = if (w == 0) false.B
        else
          (0 until w)
            .map(i => accepted(i) && dispatched(i).bits.rd_write && dispatched(i).bits.rd === op.rs1)
            .reduce(_ || _)
        val olderRs2Haz = if (w == 0) false.B
        else
          (0 until w)
            .map(i => accepted(i) && dispatched(i).bits.rd_write && dispatched(i).bits.rd === op.rs2)
            .reduce(_ || _)
        val olderWawHaz = if (w == 0) false.B
        else
          (0 until w)
            .map(i => accepted(i) && dispatched(i).bits.rd_write && dispatched(i).bits.rd === op.rd)
            .reduce(_ || _)

        val rs1Haz = rs1Used && (basePending(op.rs1) || olderRs1Haz)
        val rs2Haz = rs2Used && (basePending(op.rs2) || olderRs2Haz)
        val wawHaz = writesRd && (basePending(op.rd) || olderWawHaz)

        val (rs1CdbValid, rs1CdbData) = cdbForward(op.rs1)
        val (rs2CdbValid, rs2CdbData) = cdbForward(op.rs2)
        val rs1FromCdb                = rs1Used && rs1CdbValid
        val rs2FromCdb                = rs2Used && rs2CdbValid
        val rs1Value                  = Mux(rs1FromCdb, rs1CdbData, op.rs1_data)
        val rs2Value                  = Mux(rs2FromCdb, rs2CdbData, op.rs2_data)

        val (target, fuOk) = selectFu(op, tempFuUsed(w))
        val prevOk         = olderLaneAccepted(w, accepted)
        val typeMask       = fuTypeMask(op)
        val hasFuType      = typeMask.asUInt.orR
        val canIssue       = !flush && prevOk && fuOk && !rs1Haz && !rs2Haz && !wawHaz

        dis.ready   := canIssue
        accepted(w) := dis.valid && canIssue
        rawWait(w)  := dis.valid && !flush && prevOk && (rs1Haz || rs2Haz)
        wawWait(w)  := dis.valid && !flush && prevOk && !rawWait(w) && wawHaz
        fuBusy(w)   := dis.valid && !flush && prevOk && !rawWait(w) && !wawWait(w) && hasFuType && !fuOk
        olderBlock(w) := dis.valid && !flush && !prevOk
        noMatch(w)    := dis.valid && !flush && prevOk && !hasFuType

        acceptedWriteMask(w) := Mux(
          accepted(w) && writesRd,
          UIntToOH(op.rd, p(NumArchRegs)),
          0.U
        )
        tempFuUsed(w + 1) := tempFuUsed(w)

        when(accepted(w)) {
          val issueOp = Wire(new FuReq)
          issueOp          := op
          issueOp.fu_id    := target
          issueOp.rs1_data := rs1Value
          issueOp.rs2_data := rs2Value

          for (f <- 0 until p(NumFUs))
            when(target === f.U) {
              fuReq(f).valid := true.B
              fuReq(f).bits  := issueOp
            }

          tempFuUsed(w + 1)(target) := true.B
        }
      }

      when(flush) {
        regPending := 0.U
      }.otherwise {
        regPending := basePending | acceptedWriteMask.reduce(_ | _)
      }

      debug.raw_wait         := rawWait.asUInt.orR
      debug.waw_wait         := wawWait.asUInt.orR
      debug.fu_busy          := fuBusy.asUInt.orR
      debug.older_lane_block := olderBlock.asUInt.orR
      debug.no_matching_fu   := noMatch.asUInt.orR
    }
  }

  override def registry: NodeDimensionRegistry[SchedulerPolicyImpl] =
    SchedulerPolicyFactory
}
