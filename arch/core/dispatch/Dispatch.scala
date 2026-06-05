package arch.core.dispatch

import arch.core.scheduler.SchedulerDispatchIO
import arch.core.uop.MicroOp
import arch.configs._
import vutils.graph.{ Node, NodeType }
import chisel3._

class DispatchIO(implicit p: Parameters) extends Bundle {
  val decode    = new DispatchDecodeIO
  val regfile   = new DispatchRegfileIO
  val rob       = new DispatchRobIO
  val sb        = new DispatchStoreBufferIO
  val scheduler = Flipped(new SchedulerDispatchIO)
  val exception = new DispatchExceptionIO
}

class Dispatch(implicit p: Parameters) extends Node(new DispatchIO) {
  override def nodeType: NodeType  = DispatchMeta.Type
  override def desiredName: String = "dispatch"

  private val laneBaseReqOk = Wire(Vec(p(IssueWidth), Bool()))
  private val lanePrefixOk  = Wire(Vec(p(IssueWidth), Bool()))
  private val coreValidReq  = Wire(Vec(p(IssueWidth), Bool()))

  for (w <- 0 until p(IssueWidth)) {
    val dec = io.decode.lanes(w).bits

    io.regfile.rs1_addr(w) := dec.rs1
    io.regfile.rs2_addr(w) := dec.rs2

    io.sb.lanes(w).valid   := io.decode.lanes(w).valid
    io.sb.lanes(w).bits    := dec
    io.sb.lanes(w).rob_tag := io.rob.lanes(w).rob_tag

    io.rob.lanes(w).req.bits.decoded := dec
    io.rob.lanes(w).req.bits.sq_idx  := io.sb.lanes(w).ticket.sq_idx

    laneBaseReqOk(w) := io.decode.lanes(w).valid &&
      dec.legal &&
      !io.exception.flush &&
      io.sb.lanes(w).ready &&
      io.rob.lanes(w).req.ready
  }

  lanePrefixOk(0) := true.B

  for (w <- 1 until p(IssueWidth)) {
    val olderLaneMayBeSkipped   = !io.decode.lanes(w - 1).valid || io.exception.flush
    val olderLaneCanBePresented = laneBaseReqOk(w - 1)

    lanePrefixOk(w) := lanePrefixOk(w - 1) && (olderLaneMayBeSkipped || olderLaneCanBePresented)
  }

  for (w <- 0 until p(IssueWidth))
    coreValidReq(w) := laneBaseReqOk(w) && lanePrefixOk(w)

  for (w <- 0 until p(IssueWidth)) {
    val dec         = io.decode.lanes(w).bits
    val rs1Bypassed =
      Mux(io.rob.lanes(w).rs1_bypass.valid, io.rob.lanes(w).rs1_bypass.data, io.regfile.rs1_data(w))
    val rs2Bypassed =
      Mux(io.rob.lanes(w).rs2_bypass.valid, io.rob.lanes(w).rs2_bypass.data, io.regfile.rs2_data(w))
    val dis         = io.scheduler.reqs(w)
    val issueOp     = Wire(new MicroOp)

    issueOp.pc       := dec.pc
    issueOp.instr    := dec.instr
    issueOp.fu_type  := dec.fu_type
    issueOp.fu_id    := 0.U
    issueOp.uop      := dec.uop
    issueOp.imm      := dec.imm
    issueOp.rs1      := dec.rs1
    issueOp.rs2      := dec.rs2
    issueOp.rd       := dec.rd
    issueOp.rd_write := dec.rd_write
    issueOp.rs1_read := dec.rs1_read
    issueOp.rs2_read := dec.rs2_read
    issueOp.rs1_data := rs1Bypassed
    issueOp.rs2_data := rs2Bypassed
    issueOp.rob_tag  := io.rob.lanes(w).rob_tag
    issueOp.sq_idx   := io.sb.lanes(w).ticket.sq_idx
    issueOp.sq_seq   := io.sb.lanes(w).ticket.sq_seq

    dis.valid := coreValidReq(w)
    dis.bits  := issueOp

    io.rob.lanes(w).req.valid := dis.fire
    io.sb.lanes(w).fire       := dis.fire
  }

  for (w <- 0 until p(IssueWidth)) {
    val consumeThisLane = io.exception.flush || io.scheduler.reqs(w).fire

    if (w == 0) {
      io.decode.lanes(w).ready := consumeThisLane
    } else {
      io.decode.lanes(w).ready := io.decode.lanes(w - 1).fire && consumeThisLane
    }
  }
}
