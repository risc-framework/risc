package arch.core.scheduler.impls.policy.scoreboard

import arch.configs._
import arch.core.regfile.RegfileIsaFactory
import arch.core.scheduler._
import arch.core.uop.MicroOp
import vutils.graph.{ NodeRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.{ Mux1H, PriorityEncoder }

class ScoreboardEntry(implicit p: Parameters) extends Bundle {
  val valid = Bool()
  val op    = new MicroOp

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

    override def elaborate(io: SchedulerIO)(implicit p: Parameters): Unit = {
      val ctx     = new SchedulerContext(io)
      val regfile = RegfileIsaFactory.select(p(ISA).name)

      import ctx.{ numRegs, RegIdxW, fuTypes, olderLaneAccepted, defaultFuReqs, defaultDispatchReady }

      defaultFuReqs()
      defaultDispatchReady()

      val reg_pending_valid   = RegInit(VecInit(Seq.fill(numRegs)(false.B)))
      val reg_pending_rob     = RegInit(VecInit(Seq.fill(numRegs)(0.U(p(RobTagWidth).W))))
      val reg_completed_valid = RegInit(VecInit(Seq.fill(numRegs)(false.B)))
      val reg_completed_data  = RegInit(VecInit(Seq.fill(numRegs)(0.U(p(XLen).W))))
      val dispatch_seq        = RegInit(0.U(64.W))

      val entries = RegInit(VecInit(Seq.fill(p(NumFUs))(0.U.asTypeOf(new ScoreboardEntry))))

      val cdb_valid   = Wire(Vec(p(NumFUs), Bool()))
      val cdb_data    = Wire(Vec(p(NumFUs), UInt(p(XLen).W)))
      val cdb_rob_tag = Wire(Vec(p(NumFUs), UInt(p(RobTagWidth).W)))
      val cdb_rd      = Wire(Vec(p(NumFUs), UInt(RegIdxW.W)))

      for (i <- 0 until p(NumFUs)) {
        cdb_valid(i)   := io.fu.done(i).valid
        cdb_data(i)    := io.fu.done(i).bits.result
        cdb_rob_tag(i) := io.fu.done(i).bits.rob_tag
        cdb_rd(i)      := io.fu.done(i).bits.rd
      }

      val base_pending_valid   = Wire(Vec(numRegs, Bool()))
      val base_pending_rob     = Wire(Vec(numRegs, UInt(p(RobTagWidth).W)))
      val base_completed_valid = Wire(Vec(numRegs, Bool()))
      val base_completed_data  = Wire(Vec(numRegs, UInt(p(XLen).W)))

      for (r <- 0 until numRegs) {
        val hits = Wire(Vec(p(NumFUs), Bool()))

        for (c <- 0 until p(NumFUs))
          hits(c) := cdb_valid(c) && reg_pending_valid(r) && reg_pending_rob(r) === cdb_rob_tag(
            c
          ) && cdb_rd(c) === r.U && regfile.writable(r.U)

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

      val snooped_entries = Wire(Vec(p(NumFUs), new ScoreboardEntry))
      snooped_entries := entries

      for (i <- 0 until p(NumFUs)) {
        when(entries(i).valid && !entries(i).q1_ready) {
          val hits = Wire(Vec(p(NumFUs), Bool()))

          for (c <- 0 until p(NumFUs))
            hits(c) := cdb_valid(c) && entries(i).q1_tag === cdb_rob_tag(c)

          when(hits.asUInt.orR) {
            snooped_entries(i).q1_ready := true.B
            snooped_entries(i).v1       := Mux1H(hits, cdb_data)
          }
        }

        when(entries(i).valid && !entries(i).q2_ready) {
          val hits = Wire(Vec(p(NumFUs), Bool()))

          for (c <- 0 until p(NumFUs))
            hits(c) := cdb_valid(c) && entries(i).q2_tag === cdb_rob_tag(c)

          when(hits.asUInt.orR) {
            snooped_entries(i).q2_ready := true.B
            snooped_entries(i).v2       := Mux1H(hits, cdb_data)
          }
        }
      }

      val issued_entries = Wire(Vec(p(NumFUs), new ScoreboardEntry))
      issued_entries := snooped_entries

      for (i <- 0 until p(NumFUs)) {
        val entry         = snooped_entries(i)
        val ready_to_exec = entry.valid && entry.q1_ready && entry.q2_ready

        io.fu.reqs(i).valid         := ready_to_exec
        io.fu.reqs(i).bits          := entry.op
        io.fu.reqs(i).bits.fu_id    := i.U
        io.fu.reqs(i).bits.rs1_data := entry.v1
        io.fu.reqs(i).bits.rs2_data := entry.v2

        when(ready_to_exec && io.fu.reqs(i).ready) {
          issued_entries(i).valid := false.B
        }
      }

      val dispatched_entries = Wire(Vec(p(NumFUs), new ScoreboardEntry))
      dispatched_entries := issued_entries

      val temp_pending_valid   = Wire(Vec(p(IssueWidth) + 1, Vec(numRegs, Bool())))
      val temp_pending_rob     = Wire(Vec(p(IssueWidth) + 1, Vec(numRegs, UInt(p(RobTagWidth).W))))
      val temp_completed_valid = Wire(Vec(p(IssueWidth) + 1, Vec(numRegs, Bool())))
      val temp_completed_data  = Wire(Vec(p(IssueWidth) + 1, Vec(numRegs, UInt(p(XLen).W))))
      val temp_fu_avail        = Wire(Vec(p(IssueWidth) + 1, Vec(p(NumFUs), Bool())))
      val temp_seq             = Wire(Vec(p(IssueWidth) + 1, UInt(64.W)))
      val accepted             = Wire(Vec(p(IssueWidth), Bool()))

      temp_pending_valid(0)   := base_pending_valid
      temp_pending_rob(0)     := base_pending_rob
      temp_completed_valid(0) := base_completed_valid
      temp_completed_data(0)  := base_completed_data
      temp_seq(0)             := dispatch_seq

      for (i <- 0 until p(NumFUs))
        temp_fu_avail(0)(i) := !issued_entries(i).valid

      for (w <- 0 until p(IssueWidth)) {
        val dis = io.dispatch.reqs(w)
        val op  = dis.bits

        val fu_match_mask = Wire(Vec(p(NumFUs), Bool()))

        for (i <- 0 until p(NumFUs))
          fu_match_mask(i) := temp_fu_avail(w)(i) && fuTypes(i) === op.fu_type

        val target_fu_idx = PriorityEncoder(fu_match_mask)
        val fu_available  = fu_match_mask.asUInt.orR
        val writes_rd     = op.rd_valid && regfile.writable(op.rd)
        val prev_ok       = olderLaneAccepted(w, accepted)
        val can_accept    = fu_available && prev_ok

        dis.ready   := can_accept
        accepted(w) := dis.valid && can_accept

        temp_pending_valid(w + 1)   := temp_pending_valid(w)
        temp_pending_rob(w + 1)     := temp_pending_rob(w)
        temp_completed_valid(w + 1) := temp_completed_valid(w)
        temp_completed_data(w + 1)  := temp_completed_data(w)
        temp_fu_avail(w + 1)        := temp_fu_avail(w)
        temp_seq(w + 1)             := temp_seq(w)

        when(accepted(w)) {
          temp_fu_avail(w + 1)(target_fu_idx) := false.B

          when(writes_rd) {
            temp_pending_valid(w + 1)(op.rd)   := true.B
            temp_pending_rob(w + 1)(op.rd)     := op.rob_tag
            temp_completed_valid(w + 1)(op.rd) := false.B
            temp_completed_data(w + 1)(op.rd)  := 0.U
          }

          val entryOp = Wire(new MicroOp)
          entryOp       := op
          entryOp.fu_id := target_fu_idx

          dispatched_entries(target_fu_idx).valid := true.B
          dispatched_entries(target_fu_idx).op    := entryOp
          dispatched_entries(target_fu_idx).seq   := temp_seq(w)

          temp_seq(w + 1) := temp_seq(w) + 1.U

          val r1_used      = op.rs1_valid && regfile.readable(op.rs1)
          val r1_pending   = r1_used && temp_pending_valid(w)(op.rs1)
          val r1_completed = r1_used && !r1_pending && temp_completed_valid(w)(op.rs1)
          val r1_rob_tag   = temp_pending_rob(w)(op.rs1)
          val r1_hits      = Wire(Vec(p(NumFUs), Bool()))

          for (c <- 0 until p(NumFUs))
            r1_hits(c) := cdb_valid(c) && r1_rob_tag === cdb_rob_tag(c)

          val r1_cdb_valid = r1_pending && r1_hits.asUInt.orR
          val r1_cdb_data  = Mux1H(r1_hits, cdb_data)

          dispatched_entries(target_fu_idx).q1_ready := !r1_pending || r1_cdb_valid
          dispatched_entries(target_fu_idx).q1_tag   := r1_rob_tag
          dispatched_entries(target_fu_idx).v1       := Mux(
            r1_cdb_valid,
            r1_cdb_data,
            Mux(r1_completed, temp_completed_data(w)(op.rs1), op.rs1_data)
          )

          val r2_used      = op.rs2_valid && regfile.readable(op.rs2)
          val r2_pending   = r2_used && temp_pending_valid(w)(op.rs2)
          val r2_completed = r2_used && !r2_pending && temp_completed_valid(w)(op.rs2)
          val r2_rob_tag   = temp_pending_rob(w)(op.rs2)
          val r2_hits      = Wire(Vec(p(NumFUs), Bool()))

          for (c <- 0 until p(NumFUs))
            r2_hits(c) := cdb_valid(c) && r2_rob_tag === cdb_rob_tag(c)

          val r2_cdb_valid = r2_pending && r2_hits.asUInt.orR
          val r2_cdb_data  = Mux1H(r2_hits, cdb_data)

          dispatched_entries(target_fu_idx).q2_ready := !r2_pending || r2_cdb_valid
          dispatched_entries(target_fu_idx).q2_tag   := r2_rob_tag
          dispatched_entries(target_fu_idx).v2       := Mux(
            r2_cdb_valid,
            r2_cdb_data,
            Mux(r2_completed, temp_completed_data(w)(op.rs2), op.rs2_data)
          )
        }
      }

      when(io.ctrl.flush) {
        for (r <- 0 until numRegs) {
          reg_pending_valid(r)   := false.B
          reg_pending_rob(r)     := 0.U
          reg_completed_valid(r) := false.B
          reg_completed_data(r)  := 0.U
        }

        for (i <- 0 until p(NumFUs))
          entries(i).valid := false.B

        dispatch_seq := 0.U
      }.otherwise {
        reg_pending_valid   := temp_pending_valid(p(IssueWidth))
        reg_pending_rob     := temp_pending_rob(p(IssueWidth))
        reg_completed_valid := temp_completed_valid(p(IssueWidth))
        reg_completed_data  := temp_completed_data(p(IssueWidth))
        entries             := dispatched_entries
        dispatch_seq        := temp_seq(p(IssueWidth))
      }
    }
  }

  override def registry: NodeRegistry[SchedulerPolicyImpl] = SchedulerPolicyFactory
}
