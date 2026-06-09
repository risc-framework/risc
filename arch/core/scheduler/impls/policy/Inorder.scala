package arch.core.scheduler.impls.policy.inorder

import arch.configs._
import arch.core.scheduler._
import arch.core.fupool.{ FuReq, FuResp }
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.{ DecoupledIO, Mux1H, PriorityEncoder, ValidIO }

object InorderSchedulerPolicy extends RegisteredNodeUtils[SchedulerPolicyImpl] {
  override def utils: SchedulerPolicyImpl = new SchedulerPolicyImpl {
    override def value: String = "in-order"

    override def elaborate(
      exception: SchedulerExceptionReq,
      dispatchReq: Int => DecoupledIO[FuReq],
      fuReq: Int => DecoupledIO[FuReq],
      fuDone: Int => ValidIO[FuResp]
    )(implicit p: Parameters): Unit = {
      val numRegs = p(NumArchRegs)

      val fuTypes =
        p(FunctionalUnits).map(_.`type`.index.U(p(FuTypeWidth).W))

      def defaultFuReqs(): Unit =
        for (i <- 0 until p(NumFUs)) {
          fuReq(i).valid := false.B
          fuReq(i).bits  := 0.U.asTypeOf(new FuReq)
        }

      def defaultDispatchReady(): Unit =
        for (w <- 0 until p(IssueWidth))
          dispatchReq(w).ready := false.B

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

      val reg_pending         = RegInit(VecInit(Seq.fill(numRegs)(false.B)))
      val reg_completed_valid = RegInit(VecInit(Seq.fill(numRegs)(false.B)))
      val reg_completed_data  = RegInit(VecInit(Seq.fill(numRegs)(0.U(p(XLen).W))))

      defaultFuReqs()
      defaultDispatchReady()

      val cdb_hit   = Wire(Vec(numRegs, Vec(p(NumFUs), Bool())))
      val cdb_valid = Wire(Vec(numRegs, Bool()))
      val cdb_data  = Wire(Vec(numRegs, UInt(p(XLen).W)))

      for (r <- 0 until numRegs) {
        for (f <- 0 until p(NumFUs))
          cdb_hit(r)(f) := fuDone(f).valid && fuDone(f).bits.rd === r.U

        cdb_valid(r) := cdb_hit(r).asUInt.orR
        cdb_data(r)  := Mux1H(cdb_hit(r), (0 until p(NumFUs)).map(f => fuDone(f).bits.result))
      }

      val temp_pending         = Wire(Vec(p(IssueWidth) + 1, Vec(numRegs, Bool())))
      val temp_completed_valid = Wire(Vec(p(IssueWidth) + 1, Vec(numRegs, Bool())))
      val temp_completed_data  = Wire(Vec(p(IssueWidth) + 1, Vec(numRegs, UInt(p(XLen).W))))
      val temp_fu_used         = Wire(Vec(p(IssueWidth) + 1, Vec(p(NumFUs), Bool())))
      val accepted             = Wire(Vec(p(IssueWidth), Bool()))

      for (r <- 0 until numRegs) {
        temp_pending(0)(r)         := reg_pending(r) && !cdb_valid(r)
        temp_completed_valid(0)(r) := Mux(cdb_valid(r), true.B, reg_completed_valid(r))
        temp_completed_data(0)(r)  := Mux(cdb_valid(r), cdb_data(r), reg_completed_data(r))
      }

      for (f <- 0 until p(NumFUs))
        temp_fu_used(0)(f) := false.B

      for (w <- 0 until p(IssueWidth)) {
        val dis = dispatchReq(w)
        val op  = dis.bits

        val rs1_used = op.rs1_read
        val rs2_used = op.rs2_read
        val rd_used  = op.rd_write

        val rs1_haz = rs1_used && temp_pending(w)(op.rs1)
        val rs2_haz = rs2_used && temp_pending(w)(op.rs2)
        val waw_haz = rd_used && temp_pending(w)(op.rd)

        val rs1_from_completed = rs1_used && temp_completed_valid(w)(op.rs1)
        val rs2_from_completed = rs2_used && temp_completed_valid(w)(op.rs2)

        val rs1_value = Mux(rs1_from_completed, temp_completed_data(w)(op.rs1), op.rs1_data)
        val rs2_value = Mux(rs2_from_completed, temp_completed_data(w)(op.rs2), op.rs2_data)

        val (target, fu_ok) = selectFu(op, temp_fu_used(w))
        val prev_ok         = olderLaneAccepted(w, accepted)
        val can_issue       = prev_ok && fu_ok && !rs1_haz && !rs2_haz && !waw_haz

        dis.ready   := can_issue
        accepted(w) := dis.valid && can_issue

        temp_pending(w + 1)         := temp_pending(w)
        temp_completed_valid(w + 1) := temp_completed_valid(w)
        temp_completed_data(w + 1)  := temp_completed_data(w)
        temp_fu_used(w + 1)         := temp_fu_used(w)

        when(accepted(w)) {
          val issueOp = Wire(new FuReq)
          issueOp          := op
          issueOp.fu_id    := target
          issueOp.rs1_data := rs1_value
          issueOp.rs2_data := rs2_value

          for (f <- 0 until p(NumFUs))
            when(target === f.U) {
              fuReq(f).valid := true.B
              fuReq(f).bits  := issueOp
            }

          temp_fu_used(w + 1)(target) := true.B

          when(rd_used) {
            temp_pending(w + 1)(op.rd)         := true.B
            temp_completed_valid(w + 1)(op.rd) := false.B
            temp_completed_data(w + 1)(op.rd)  := 0.U
          }
        }
      }

      when(exception.flush) {
        reg_pending.foreach(_ := false.B)
        reg_completed_valid.foreach(_ := false.B)
        reg_completed_data.foreach(_ := 0.U)
      }.otherwise {
        reg_pending         := temp_pending(p(IssueWidth))
        reg_completed_valid := temp_completed_valid(p(IssueWidth))
        reg_completed_data  := temp_completed_data(p(IssueWidth))
      }
    }
  }

  override def registry: NodeDimensionRegistry[SchedulerPolicyImpl] =
    SchedulerPolicyFactory
}
