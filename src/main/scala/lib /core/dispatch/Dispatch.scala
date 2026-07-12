package arch.core.dispatch

import arch.configs._
import arch.core.decode.DecodedPacket
import arch.core.fupool.FuReq
import arch.core.regfile.{ RegfileReadReq, RegfileReadResp }
import vutils.graph.Node
import chisel3._

class Dispatch(implicit p: Parameters) extends Node[Parameters]("dispatch") {
  val flush      = in[Bool]
  val decoded    = inDVec[DecodedPacket](p => p(IssueWidth))
  val rs1Read    = outVVec[RegfileReadReq](p => p(IssueWidth))
  val rs2Read    = outVVec[RegfileReadReq](p => p(IssueWidth))
  val rs1Data    = inVVec[RegfileReadResp](p => p(IssueWidth))
  val rs2Data    = inVVec[RegfileReadResp](p => p(IssueWidth))
  val robReq     = outDVec[DispatchRobPacket](p => p(IssueWidth))
  val robResp    = inVec[DispatchRobResp](p => p(IssueWidth))
  val dispatched = outDVec[FuReq](p => p(IssueWidth))

  private val laneBaseReqOk  = Wire(Vec(p(IssueWidth), Bool()))
  private val lanePrefixFire = Wire(Vec(p(IssueWidth), Bool()))
  private val coreValidReq   = Wire(Vec(p(IssueWidth), Bool()))

  for (w <- 0 until p(IssueWidth)) {
    val dec = decoded.in.lanes(w).bits

    rs1Read.out.lanes(w).valid     := decoded.in.lanes(w).valid && dec.rs1_read && !flush.in
    rs1Read.out.lanes(w).bits.addr := dec.rs1

    rs2Read.out.lanes(w).valid     := decoded.in.lanes(w).valid && dec.rs2_read && !flush.in
    rs2Read.out.lanes(w).bits.addr := dec.rs2

    robReq.out.lanes(w).bits.active  := decoded.in.lanes(w).valid && dec.legal && !flush.in
    robReq.out.lanes(w).bits.decoded := dec

    laneBaseReqOk(w) := decoded.in.lanes(w).valid && dec.legal && !flush.in && robReq.out
      .lanes(w)
      .ready
  }

  lanePrefixFire(0) := true.B

  for (w <- 1 until p(IssueWidth)) {
    val olderLaneMayBeSkipped = !decoded.in.lanes(w - 1).valid || flush.in
    val olderLaneDidDispatch  = dispatched.out.lanes(w - 1).fire
    lanePrefixFire(w) := lanePrefixFire(w - 1) && (olderLaneMayBeSkipped || olderLaneDidDispatch)
  }

  for (w <- 0 until p(IssueWidth))
    coreValidReq(w) := laneBaseReqOk(w) && lanePrefixFire(w)

  for (w <- 0 until p(IssueWidth)) {
    val dec = decoded.in.lanes(w).bits

    val rs1Raw = Mux(rs1Data.in.lanes(w).valid, rs1Data.in.lanes(w).bits.data, 0.U(p(XLen).W))
    val rs2Raw = Mux(rs2Data.in.lanes(w).valid, rs2Data.in.lanes(w).bits.data, 0.U(p(XLen).W))

    val rs1Bypassed =
      Mux(robResp.in.lanes(w).rs1_bypass_valid, robResp.in.lanes(w).rs1_bypass_data, rs1Raw)
    val rs2Bypassed =
      Mux(robResp.in.lanes(w).rs2_bypass_valid, robResp.in.lanes(w).rs2_bypass_data, rs2Raw)

    val dis     = dispatched.out.lanes(w)
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
    issueOp.sq_idx   := robResp.in.lanes(w).sq_idx
    issueOp.sq_seq   := robResp.in.lanes(w).sq_seq

    dis.valid := coreValidReq(w)
    dis.bits  := issueOp

    robReq.out.lanes(w).valid := dis.fire
  }

  for (w <- 0 until p(IssueWidth)) {
    val consumeThisLane = flush.in || dispatched.out.lanes(w).fire
    decoded.in.lanes(w).ready := lanePrefixFire(w) && consumeThisLane
  }
}
