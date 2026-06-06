package arch.core.regfile

import arch.core.dispatch.DispatchRegfileIO
import arch.core.rob.RobRegfileIO
import arch.configs._
import chisel3._
import vutils.graph.{ Node, NodeConfig, NodeSelector, NodeType }

class RegfileIO(implicit p: Parameters) extends Bundle {
  val dispatch = Flipped(new DispatchRegfileIO)
  val rob      = Flipped(new RobRegfileIO)
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
      when(io.rob.write(w).valid && io.rob.write(w).bits.addr === addr.U) {
        regsSeq(addr) := io.rob.write(w).bits.data
      }

  for (w <- 0 until p(IssueWidth)) {
    val rs1Raw = regsVec(io.dispatch.rs1_addr(w))
    val rs2Raw = regsVec(io.dispatch.rs2_addr(w))

    if (p(IsRegfileUseBypass)) {
      var rs1Bypassed = rs1Raw
      var rs2Bypassed = rs2Raw

      for (i <- 0 until p(IssueWidth)) {
        val matchRs1 =
          io.rob.write(i).valid && io.dispatch.rs1_addr(w) === io.rob.write(i).bits.addr
        val matchRs2 =
          io.rob.write(i).valid && io.dispatch.rs2_addr(w) === io.rob.write(i).bits.addr

        rs1Bypassed = Mux(matchRs1, io.rob.write(i).bits.data, rs1Bypassed)
        rs2Bypassed = Mux(matchRs2, io.rob.write(i).bits.data, rs2Bypassed)
      }

      io.dispatch.rs1_data(w) := rs1Bypassed
      io.dispatch.rs2_data(w) := rs2Bypassed
    } else {
      io.dispatch.rs1_data(w) := rs1Raw
      io.dispatch.rs2_data(w) := rs2Raw
    }
  }
}
