package arch.core.dispatch

import arch.configs._
import arch.core.decode.DecodedPacket
import arch.core.fupool.FuReq
import arch.core.regfile.{ RegfileReadReq, RegfileReadResp }
import arch.core.sb.StoreAddressBundle
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
  val storeAddr  = outVVec[StoreAddressBundle](p => p(IssueWidth))

  private val laneBaseReqOk  = Wire(Vec(p(IssueWidth), Bool()))
  private val lanePrefixFire = Wire(Vec(p(IssueWidth), Bool()))
  private val coreValidReq   = Wire(Vec(p(IssueWidth), Bool()))
  private val storeAddrValidReg = RegInit(VecInit(Seq.fill(p(IssueWidth))(false.B)))
  private val storeAddrBitsReg  = Reg(Vec(p(IssueWidth), new StoreAddressBundle))

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
    val rs1Pending = WireDefault(robResp.in.lanes(w).rs1_bypass_pending)
    val rs2Pending = WireDefault(robResp.in.lanes(w).rs2_bypass_pending)
    val rs1Tag     = WireDefault(robResp.in.lanes(w).rs1_bypass_tag)
    val rs2Tag     = WireDefault(robResp.in.lanes(w).rs2_bypass_tag)

    for (i <- 0 until w) {
      val olderDec    = decoded.in.lanes(i).bits
      val olderWrites = dispatched.out.lanes(i).fire && olderDec.rd_write && olderDec.rd =/= 0.U

      when(olderWrites && dec.rs1_read && olderDec.rd === dec.rs1) {
        rs1Pending := true.B
        rs1Tag     := robResp.in.lanes(i).rob_tag
      }

      when(olderWrites && dec.rs2_read && olderDec.rd === dec.rs2) {
        rs2Pending := true.B
        rs2Tag     := robResp.in.lanes(i).rob_tag
      }
    }

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
    issueOp.rs1_pending := dec.rs1_read && rs1Pending
    issueOp.rs2_pending := dec.rs2_read && rs2Pending
    issueOp.rs1_tag     := rs1Tag
    issueOp.rs2_tag     := rs2Tag
    issueOp.rob_tag  := robResp.in.lanes(w).rob_tag
    issueOp.sq_idx   := robResp.in.lanes(w).sq_idx
    issueOp.sq_seq   := robResp.in.lanes(w).sq_seq

    dis.valid := coreValidReq(w)
    dis.bits  := issueOp

    // Publish a store address as soon as its base is available at dispatch.
    // The one-cycle sideband register keeps ROB bypass and address generation
    // out of the StoreBuffer write path. A younger load cannot query forwarding
    // until the following cycle, so this delay does not add a load bubble.
    val publishStoreAddr = dis.fire && dec.isStore && !issueOp.rs1_pending

    storeAddr.out.lanes(w).valid := storeAddrValidReg(w) && !flush.in
    storeAddr.out.lanes(w).bits  := storeAddrBitsReg(w)

    when(flush.in) {
      storeAddrValidReg(w) := false.B
    }.otherwise {
      storeAddrValidReg(w) := publishStoreAddr
      when(publishStoreAddr) {
        storeAddrBitsReg(w).sq_idx := issueOp.sq_idx
        storeAddrBitsReg(w).addr   := issueOp.rs1_data + issueOp.imm
      }
    }

    robReq.out.lanes(w).valid := dis.fire
  }

  for (w <- 0 until p(IssueWidth)) {
    val consumeThisLane = flush.in || dispatched.out.lanes(w).fire
    decoded.in.lanes(w).ready := lanePrefixFire(w) && consumeThisLane
  }
}
