package arch.core.regfile

import arch.configs._
import chisel3._
import chisel3.util.log2Ceil

class RegfileReadIO(implicit p: Parameters) extends Bundle {
  val rs1_addr = Input(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
  val rs2_addr = Input(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
  val rs1_data = Output(Vec(p(IssueWidth), UInt(p(XLen).W)))
  val rs2_data = Output(Vec(p(IssueWidth), UInt(p(XLen).W)))
}
