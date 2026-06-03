package arch.core.regfile

import arch.configs._
import chisel3._
import vutils.graph.{ Node, NodeConfig, NodeSelector, NodeType }

class RegfileIO(implicit p: Parameters) extends Bundle {
  val read  = new RegfileReadIO
  val write = new RegfileWriteIO
  val debug = new RegfileDebugIO
}

class Regfile(implicit p: Parameters) extends Node(new RegfileIO) {
  private val cfg = NodeConfig(
    selector = NodeSelector(
      RegfileDims.ISA -> p(ISA).name
    )
  )

  override def nodeType: NodeType  = RegfileMeta.Type
  override def desiredName: String = s"regfile_${cfg.selector.canonicalName}"

  private val isaImpl = RegfileIsaFactory.select(cfg)

  private val regsSeq = Seq.tabulate(p(NumArchRegs)) { addr =>
    val reg = RegInit(isaImpl.initValue(addr).U(p(XLen).W))
    reg.suggestName(isaImpl.regName(addr))
    reg
  }

  private val regsVec = VecInit(regsSeq)

  for (addr <- 0 until p(NumArchRegs))
    for (w <- 0 until p(IssueWidth))
      when(io.write.en(w) && io.write.addr(w) === addr.U) {
        regsSeq(addr) := io.write.data(w)
      }

  for (w <- 0 until p(IssueWidth)) {
    val rs1Raw = regsVec(io.read.rs1_addr(w))
    val rs2Raw = regsVec(io.read.rs2_addr(w))

    if (p(IsRegfileUseBypass)) {
      var rs1Bypassed = rs1Raw
      var rs2Bypassed = rs2Raw

      for (i <- 0 until p(IssueWidth)) {
        val matchRs1 = io.write.en(i) && io.read.rs1_addr(w) === io.write.addr(i)
        val matchRs2 = io.write.en(i) && io.read.rs2_addr(w) === io.write.addr(i)

        rs1Bypassed = Mux(matchRs1, io.write.data(i), rs1Bypassed)
        rs2Bypassed = Mux(matchRs2, io.write.data(i), rs2Bypassed)
      }

      io.read.rs1_data(w) := rs1Bypassed
      io.read.rs2_data(w) := rs2Bypassed
    } else {
      io.read.rs1_data(w) := rs1Raw
      io.read.rs2_data(w) := rs2Raw
    }
  }

  for (w <- 0 until p(IssueWidth)) {
    io.debug.reg_we(w)   := io.write.en(w)
    io.debug.reg_addr(w) := io.write.addr(w)
    io.debug.reg_data(w) := io.write.data(w)
  }
}
