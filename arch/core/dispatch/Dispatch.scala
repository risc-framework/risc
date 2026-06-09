package arch.core.dispatch

import arch.core.decode.DecodedPacket
import arch.core.fupool.FuReq
import arch.configs._
import vutils.graph.{ Node, NodeType }
import chisel3._

class Dispatch(implicit p: Parameters) extends Node[Parameters]("dispatch") {
  val decode       = inDVec[DecodedPacket](p => p(IssueWidth))
  val regfileReq   = out[DispatchRegfileReq]
  val regfileResp  = in[DispatchRegfileResp]
  val robReq       = outDVec[DispatchRobPacket](p => p(IssueWidth))
  val robResp      = inVec[DispatchRobResp](p => p(IssueWidth))
  val sbReq        = outVec[DispatchStoreBufferReq](p => p(IssueWidth))
  val sbResp       = inVec[DispatchStoreBufferResp](p => p(IssueWidth))
  val schedulerReq = outDVec[FuReq](p => p(IssueWidth))
  val exception    = in[DispatchExceptionReq]

  private val laneBaseReqOk = Wire(Vec(p(IssueWidth), Bool()))
  private val lanePrefixOk  = Wire(Vec(p(IssueWidth), Bool()))
  private val coreValidReq  = Wire(Vec(p(IssueWidth), Bool()))

  for (w <- 0 until p(IssueWidth)) {
    val dec = decode.in.lanes(w).bits

    regfileReq.out.rs1_addr(w) := dec.rs1
    regfileReq.out.rs2_addr(w) := dec.rs2

    sbReq.out.lanes(w).valid   := decode.in.lanes(w).valid
    sbReq.out.lanes(w).bits    := dec
    sbReq.out.lanes(w).rob_tag := robResp.in.lanes(w).rob_tag

    robReq.out.lanes(w).bits.decoded := dec
    robReq.out.lanes(w).bits.sq_idx  := sbResp.in.lanes(w).ticket.sq_idx

    laneBaseReqOk(w) := decode.in.lanes(w).valid &&
      dec.legal &&
      !exception.in.flush &&
      sbResp.in.lanes(w).ready &&
      robReq.out.lanes(w).ready
  }

  lanePrefixOk(0) := true.B

  for (w <- 1 until p(IssueWidth)) {
    val olderLaneMayBeSkipped   = !decode.in.lanes(w - 1).valid || exception.in.flush
    val olderLaneCanBePresented = laneBaseReqOk(w - 1)

    lanePrefixOk(w) := lanePrefixOk(w - 1) && (olderLaneMayBeSkipped || olderLaneCanBePresented)
  }

  for (w <- 0 until p(IssueWidth))
    coreValidReq(w) := laneBaseReqOk(w) && lanePrefixOk(w)

  for (w <- 0 until p(IssueWidth)) {
    val dec = decode.in.lanes(w).bits

    val rs1Bypassed =
      Mux(
        robResp.in.lanes(w).rs1_bypass_valid,
        robResp.in.lanes(w).rs1_bypass_data,
        regfileResp.in.rs1_data(w)
      )

    val rs2Bypassed =
      Mux(
        robResp.in.lanes(w).rs2_bypass_valid,
        robResp.in.lanes(w).rs2_bypass_data,
        regfileResp.in.rs2_data(w)
      )

    val dis     = schedulerReq.out.lanes(w)
    val issueOp = Wire(new FuReq)

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
    issueOp.rob_tag  := robResp.in.lanes(w).rob_tag
    issueOp.sq_idx   := sbResp.in.lanes(w).ticket.sq_idx
    issueOp.sq_seq   := sbResp.in.lanes(w).ticket.sq_seq

    dis.valid := coreValidReq(w)
    dis.bits  := issueOp

    robReq.out.lanes(w).valid := dis.fire
    sbReq.out.lanes(w).fire   := dis.fire
  }

  for (w <- 0 until p(IssueWidth)) {
    val consumeThisLane = exception.in.flush || schedulerReq.out.lanes(w).fire

    if (w == 0) {
      decode.in.lanes(w).ready := consumeThisLane
    } else {
      decode.in.lanes(w).ready := decode.in.lanes(w - 1).fire && consumeThisLane
    }
  }
}
