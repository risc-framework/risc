package arch.core.regfile

import arch.configs._
import vutils.graph.{ Node, NodeConfig, NodeSelector }
import chisel3._

class Regfile(implicit p: Parameters) extends Node[Parameters]("regfile") {
  override protected def cfg: NodeConfig = NodeConfig(
    selector = NodeSelector(
      RegfileDims.ISA -> p(ISA).name
    )
  )

  val rs1Read = inVVec[RegfileReadReq](p => p(IssueWidth))
  val rs2Read = inVVec[RegfileReadReq](p => p(IssueWidth))
  val rs1Data = outVVec[RegfileReadResp](p => p(IssueWidth))
  val rs2Data = outVVec[RegfileReadResp](p => p(IssueWidth))
  val rdWrite = inVVec[RegfileWrite](p => p(CommitWidth))

  private val isaImpl = RegfileIsaFactory.select(cfg)

  private val regsSeq = Seq.tabulate(p(NumArchRegs)) { addr =>
    val reg = RegInit(isaImpl.initValue(addr).U(p(XLen).W))
    reg.suggestName(isaImpl.regName(addr))
    reg
  }

  private val regsVec = VecInit(regsSeq)

  for (addr <- 0 until p(NumArchRegs))
    for (w <- 0 until p(CommitWidth))
      when(rdWrite.in.lanes(w).valid && rdWrite.in.lanes(w).bits.addr === addr.U) {
        regsSeq(addr) := rdWrite.in.lanes(w).bits.data
      }

  private def bypass(readAddr: UInt, raw: UInt): UInt = {
    var data = raw

    if (p(IsRegfileUseBypass)) {
      for (w <- 0 until p(CommitWidth)) {
        val hit = rdWrite.in.lanes(w).valid && rdWrite.in.lanes(w).bits.addr === readAddr
        data = Mux(hit, rdWrite.in.lanes(w).bits.data, data)
      }
    }

    data
  }

  for (w <- 0 until p(IssueWidth)) {
    val rs1Addr = rs1Read.in.lanes(w).bits.addr
    val rs2Addr = rs2Read.in.lanes(w).bits.addr
    val rs1Raw  = regsVec(rs1Addr)
    val rs2Raw  = regsVec(rs2Addr)

    rs1Data.out.lanes(w).valid     := rs1Read.in.lanes(w).valid
    rs1Data.out.lanes(w).bits.data := bypass(rs1Addr, rs1Raw)

    rs2Data.out.lanes(w).valid     := rs2Read.in.lanes(w).valid
    rs2Data.out.lanes(w).bits.data := bypass(rs2Addr, rs2Raw)
  }
}
