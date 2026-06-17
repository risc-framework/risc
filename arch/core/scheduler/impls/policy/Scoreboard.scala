package arch.core.scheduler.impls.policy.scoreboard

import arch.configs._
import arch.core.fupool.{ FuReq, FuResp }
import arch.core.scheduler._
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.{ DecoupledIO, Mux1H, PriorityEncoder }

object ScoreboardSchedulerPolicy extends RegisteredNodeUtils[SchedulerPolicyImpl] {
  override def utils: SchedulerPolicyImpl = new SchedulerPolicyImpl {
    override def value: String = "scoreboard"

    override def elaborate(
      exception: SchedulerExceptionReq,
      dispatchReq: Int => DecoupledIO[FuReq],
      fuReq: Int => DecoupledIO[FuReq],
      fuDone: Int => DecoupledIO[FuResp]
    )(implicit p: Parameters): Unit = {
      val fuTypes = p(FunctionalUnits).map(_.`type`.index.U(p(FuTypeWidth).W))

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

      def fuMatchMask(op: FuReq, used: Vec[Bool]): Vec[Bool] = {
        val mask = Wire(Vec(p(NumFUs), Bool()))
        for (i <- 0 until p(NumFUs))
          mask(i) := !used(i) && fuReq(i).ready && fuTypes(i) === op.fu_type
        mask
      }

      def selectFu(op: FuReq, used: Vec[Bool]): (UInt, Bool) = {
        val mask = fuMatchMask(op, used)
        (PriorityEncoder(mask), mask.asUInt.orR)
      }

      def olderLaneAccepted(w: Int, accepted: Vec[Bool]): Bool =
        if (w == 0) true.B else !dispatchReq(w - 1).valid || accepted(w - 1)

      val regPending = RegInit(VecInit(Seq.fill(p(NumArchRegs))(false.B)))

      defaultFuReqs()
      defaultDispatchReady()
      defaultFuDoneReady()

      val cdbHit   = Wire(Vec(p(NumArchRegs), Vec(p(NumFUs), Bool())))
      val cdbValid = Wire(Vec(p(NumArchRegs), Bool()))
      val cdbData  = Wire(Vec(p(NumArchRegs), UInt(p(XLen).W)))

      for (r <- 0 until p(NumArchRegs)) {
        for (f <- 0 until p(NumFUs))
          if (r == 0) cdbHit(r)(f) := false.B
          else cdbHit(r)(f)        := fuDone(f).fire && fuDone(f).bits.rd === r.U

        cdbValid(r) := cdbHit(r).asUInt.orR
        cdbData(r)  := Mux1H(cdbHit(r), (0 until p(NumFUs)).map(f => fuDone(f).bits.result))
      }

      val tempPending = Wire(Vec(p(IssueWidth) + 1, Vec(p(NumArchRegs), Bool())))
      val tempFuUsed  = Wire(Vec(p(IssueWidth) + 1, Vec(p(NumFUs), Bool())))
      val accepted    = Wire(Vec(p(IssueWidth), Bool()))

      for (r <- 0 until p(NumArchRegs))
        tempPending(0)(r) := regPending(r) && !cdbValid(r)

      for (f <- 0 until p(NumFUs))
        tempFuUsed(0)(f) := false.B

      for (w <- 0 until p(IssueWidth)) {
        val dis = dispatchReq(w)
        val op  = dis.bits

        val rs1Used  = op.rs1_read
        val rs2Used  = op.rs2_read
        val writesRd = op.rd_write

        val rs1Haz = rs1Used && tempPending(w)(op.rs1)
        val rs2Haz = rs2Used && tempPending(w)(op.rs2)
        val wawHaz = writesRd && tempPending(w)(op.rd)

        val rs1FromCdb = rs1Used && cdbValid(op.rs1)
        val rs2FromCdb = rs2Used && cdbValid(op.rs2)
        val rs1Value   = Mux(rs1FromCdb, cdbData(op.rs1), op.rs1_data)
        val rs2Value   = Mux(rs2FromCdb, cdbData(op.rs2), op.rs2_data)

        val (target, fuOk) = selectFu(op, tempFuUsed(w))
        val prevOk         = olderLaneAccepted(w, accepted)
        val canIssue       = !exception.flush && prevOk && fuOk && !rs1Haz && !rs2Haz && !wawHaz

        dis.ready   := canIssue
        accepted(w) := dis.valid && canIssue

        tempPending(w + 1) := tempPending(w)
        tempFuUsed(w + 1)  := tempFuUsed(w)

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

          when(writesRd) {
            tempPending(w + 1)(op.rd) := true.B
          }
        }
      }

      when(exception.flush) {
        regPending.foreach(_ := false.B)
      }.otherwise {
        regPending := tempPending(p(IssueWidth))
      }
    }
  }

  override def registry: NodeDimensionRegistry[SchedulerPolicyImpl] =
    SchedulerPolicyFactory
}
