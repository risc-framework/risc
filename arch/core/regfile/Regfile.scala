package arch.core.regfile

import arch.configs._
import arch.core.dispatch.{ DispatchRegfileReq, DispatchRegfileResp }
import chisel3._
import vutils.graph.{ Node, NodeConfig, NodeSelector }

class Regfile(implicit p: Parameters) extends Node[Parameters]("regfile") {
  override protected def cfg: NodeConfig = NodeConfig(
    selector = NodeSelector(
      RegfileDims.ISA -> p(ISA).name
    )
  )

  val dispatchReq  = in[DispatchRegfileReq]
  val dispatchResp = out[DispatchRegfileResp]
  val robWrite     = inVVec[RegfileWrite](p => p(CommitWidth))

  private val isaImpl = RegfileIsaFactory.select(cfg)

  private val regsSeq = Seq.tabulate(p(NumArchRegs)) { addr =>
    val reg = RegInit(isaImpl.initValue(addr).U(p(XLen).W))
    reg.suggestName(isaImpl.regName(addr))
    reg
  }

  private val regsVec = VecInit(regsSeq)

  for (addr <- 0 until p(NumArchRegs))
    for (w <- 0 until p(CommitWidth))
      when(robWrite.in.lanes(w).valid && robWrite.in.lanes(w).bits.addr === addr.U) {
        regsSeq(addr) := robWrite.in.lanes(w).bits.data
      }

  for (w <- 0 until p(IssueWidth)) {
    val rs1Raw = regsVec(dispatchReq.in.rs1_addr(w))
    val rs2Raw = regsVec(dispatchReq.in.rs2_addr(w))

    if (p(IsRegfileUseBypass)) {
      var rs1Bypassed = rs1Raw
      var rs2Bypassed = rs2Raw

      for (i <- 0 until p(CommitWidth)) {
        val matchRs1 = robWrite.in.lanes(i).valid && dispatchReq.in
          .rs1_addr(w) === robWrite.in.lanes(i).bits.addr
        val matchRs2 = robWrite.in.lanes(i).valid && dispatchReq.in
          .rs2_addr(w) === robWrite.in.lanes(i).bits.addr

        rs1Bypassed = Mux(matchRs1, robWrite.in.lanes(i).bits.data, rs1Bypassed)
        rs2Bypassed = Mux(matchRs2, robWrite.in.lanes(i).bits.data, rs2Bypassed)
      }

      dispatchResp.out.rs1_data(w) := rs1Bypassed
      dispatchResp.out.rs2_data(w) := rs2Bypassed
    } else {
      dispatchResp.out.rs1_data(w) := rs1Raw
      dispatchResp.out.rs2_data(w) := rs2Raw
    }
  }
}
