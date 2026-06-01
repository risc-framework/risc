package arch.node.regfile

import arch.configs._
import vutils.graph.{ Node, NodeType, NodeConfig, NodeSelector }
import chisel3._
import chisel3.util.log2Ceil

class RegfileIO(implicit p: Parameters) extends Bundle {
  val decode = new RegfileDecodeIO
  val read   = new RegfileReadIO
  val write  = new RegfileWriteIO
  val debug  = new RegfileDebugIO
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
  private val regIdxW = log2Ceil(p(NumArchRegs))

  private val readableVec = VecInit(
    Seq.tabulate(p(NumArchRegs))(addr => isaImpl.readable(addr.U(regIdxW.W)))
  )
  private val writableVec = VecInit(
    Seq.tabulate(p(NumArchRegs))(addr => isaImpl.writable(addr.U(regIdxW.W)))
  )

  private val regsSeq = Seq.tabulate(p(NumArchRegs)) { addr =>
    val init = isaImpl.initValue(addr).U(p(XLen).W)
    val reg  = RegInit(init)
    reg.suggestName(isaImpl.regName(addr))
    reg
  }

  private val regsVec = VecInit(regsSeq)

  for (w <- 0 until p(IssueWidth)) {
    val instr = io.decode.instr(w)
    val rs1   = isaImpl.getRs1(instr)
    val rs2   = isaImpl.getRs2(instr)
    val rd    = isaImpl.getRd(instr)

    io.decode.rs1_addr(w) := rs1
    io.decode.rs2_addr(w) := rs2
    io.decode.rd_addr(w)  := rd
    io.decode.rs1_read(w) := isaImpl.readable(rs1)
    io.decode.rs2_read(w) := isaImpl.readable(rs2)
    io.decode.rd_write(w) := isaImpl.writable(rd)
  }

  for (addr <- 0 until p(NumArchRegs))
    for (w <- 0 until p(IssueWidth))
      when(io.write.en(w) && writableVec(addr) && io.write.addr(w) === addr.U(regIdxW.W)) {
        regsSeq(addr) := io.write.data(w)
      }

  for (w <- 0 until p(IssueWidth)) {
    val rs1Raw = regsVec(io.read.rs1_addr(w))
    val rs2Raw = regsVec(io.read.rs2_addr(w))

    if (p(IsRegfileUseBypass)) {
      var rs1Bypassed = rs1Raw
      var rs2Bypassed = rs2Raw

      for (i <- 0 until p(IssueWidth)) {
        val writeOk  = io.write.en(i) && writableVec(io.write.addr(i))
        val matchRs1 = writeOk && io.read.rs1_addr(w) === io.write.addr(i)
        val matchRs2 = writeOk && io.read.rs2_addr(w) === io.write.addr(i)

        rs1Bypassed = Mux(matchRs1, io.write.data(i), rs1Bypassed)
        rs2Bypassed = Mux(matchRs2, io.write.data(i), rs2Bypassed)
      }

      io.read.rs1_data(w) := Mux(readableVec(io.read.rs1_addr(w)), rs1Bypassed, 0.U)
      io.read.rs2_data(w) := Mux(readableVec(io.read.rs2_addr(w)), rs2Bypassed, 0.U)
    } else {
      io.read.rs1_data(w) := Mux(readableVec(io.read.rs1_addr(w)), rs1Raw, 0.U)
      io.read.rs2_data(w) := Mux(readableVec(io.read.rs2_addr(w)), rs2Raw, 0.U)
    }
  }

  for (w <- 0 until p(IssueWidth)) {
    io.debug.reg_we(w)   := io.write.en(w) && writableVec(io.write.addr(w))
    io.debug.reg_addr(w) := io.write.addr(w)
    io.debug.reg_data(w) := io.write.data(w)
  }
}
