package arch.core.regfile

import arch.configs._
import chisel3._
import chisel3.util.log2Ceil

class Regfile(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_regfile"

  val utils = RegfileUtilsFactory.getOrThrow(p(ISA).name)

  // NOTE: Renaming to be impled
  val rs1_preg   = IO(Input(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W))))
  val rs2_preg   = IO(Input(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W))))
  val write_preg = IO(Input(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W))))
  val write_data = IO(Input(Vec(p(IssueWidth), UInt(p(XLen).W))))
  val write_en   = IO(Input(Vec(p(IssueWidth), Bool())))

  val rs1_data = IO(Output(Vec(p(IssueWidth), UInt(p(XLen).W))))
  val rs2_data = IO(Output(Vec(p(IssueWidth), UInt(p(XLen).W))))

  val isWritable = Seq.tabulate(p(NumArchRegs)) { addr =>
    utils.extraInfo.find(_.addr == addr).forall(_.writable)
  }

  val readableVec = VecInit(Seq.tabulate(p(NumArchRegs)) { addr =>
    utils.extraInfo.find(_.addr == addr).forall(_.readable).B
  })

  val regsSeq = Seq.tabulate(p(NumArchRegs)) { addr =>
    val regInfo = utils.extraInfo.find(_.addr == addr)
    val initVal = regInfo
      .map(r => (r.initValue & ((1L << p(XLen)) - 1)).U(p(XLen).W))
      .getOrElse(0.U(p(XLen).W))

    val r    = RegInit(initVal)
    val name = regInfo.map(_.name).getOrElse(s"x$addr")
    r.suggestName(name)
    r
  }

  val regsVec = VecInit(regsSeq)

  for (i <- 0 until p(NumArchRegs))
    if (isWritable(i)) {
      for (w <- 0 until p(IssueWidth))
        when(write_en(w) && write_preg(w) === i.U) {
          regsSeq(i) := write_data(w)
        }
    }

  for (i <- 0 until p(IssueWidth)) {
    val rs1_raw = regsVec(rs1_preg(i))
    val rs2_raw = regsVec(rs2_preg(i))

    if (p(IsRegfileUseBypass)) {
      var rs1_bypassed = rs1_raw
      var rs2_bypassed = rs2_raw

      for (w <- 0 until p(IssueWidth)) {
        val is_w      = VecInit(isWritable.map(_.B))(write_preg(w))
        val match_rs1 = write_en(w) && is_w && (rs1_preg(i) === write_preg(w))
        val match_rs2 = write_en(w) && is_w && (rs2_preg(i) === write_preg(w))

        rs1_bypassed = Mux(match_rs1, write_data(w), rs1_bypassed)
        rs2_bypassed = Mux(match_rs2, write_data(w), rs2_bypassed)
      }

      rs1_data(i) := Mux(readableVec(rs1_preg(i)), rs1_bypassed, 0.U)
      rs2_data(i) := Mux(readableVec(rs2_preg(i)), rs2_bypassed, 0.U)
    } else {
      rs1_data(i) := Mux(readableVec(rs1_preg(i)), rs1_raw, 0.U)
      rs2_data(i) := Mux(readableVec(rs2_preg(i)), rs2_raw, 0.U)
    }
  }
}
