package arch.node.regfile

import arch.configs._
import chisel3._
import chisel3.util.log2Ceil

class RegfileDecodeIO(implicit p: Parameters) extends Bundle {
  val instr    = Input(Vec(p(IssueWidth), UInt(p(ILen).W)))
  val rs1_addr = Output(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
  val rs2_addr = Output(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
  val rd_addr  = Output(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
  val rs1_read = Output(Vec(p(IssueWidth), Bool()))
  val rs2_read = Output(Vec(p(IssueWidth), Bool()))
  val rd_write = Output(Vec(p(IssueWidth), Bool()))
}

class RegfileReadIO(implicit p: Parameters) extends Bundle {
  val rs1_addr = Input(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
  val rs2_addr = Input(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
  val rs1_data = Output(Vec(p(IssueWidth), UInt(p(XLen).W)))
  val rs2_data = Output(Vec(p(IssueWidth), UInt(p(XLen).W)))
}

class RegfileWriteIO(implicit p: Parameters) extends Bundle {
  val en   = Input(Vec(p(IssueWidth), Bool()))
  val addr = Input(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
  val data = Input(Vec(p(IssueWidth), UInt(p(XLen).W)))
}

class RegfileDebugIO(implicit p: Parameters) extends Bundle {
  val reg_we   = Output(Vec(p(IssueWidth), Bool()))
  val reg_addr = Output(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
  val reg_data = Output(Vec(p(IssueWidth), UInt(p(XLen).W)))
}
