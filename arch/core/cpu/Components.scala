package arch.core.cpu

import arch.configs._
import chisel3._
import chisel3.util.log2Ceil

class DebugIO(implicit p: Parameters) extends Bundle {
  val cycle_count   = Output(UInt(64.W))
  val instret_count = Output(UInt(64.W))

  val instret  = Output(Vec(p(IssueWidth), Bool()))
  val pc       = Output(Vec(p(IssueWidth), UInt(p(XLen).W)))
  val instr    = Output(Vec(p(IssueWidth), UInt(p(ILen).W)))
  val reg_we   = Output(Vec(p(IssueWidth), Bool()))
  val reg_addr = Output(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
  val reg_data = Output(Vec(p(IssueWidth), UInt(p(XLen).W)))

  val branch_taken  = Output(Bool())
  val branch_source = Output(UInt(p(XLen).W))
  val branch_target = Output(UInt(p(XLen).W))

  val l1_icache_access = Output(Bool())
  val l1_icache_miss   = Output(Bool())
  val l1_dcache_access = Output(Bool())
  val l1_dcache_miss   = Output(Bool())

  val flush_cycle    = Output(Bool())
  val bpu_mispredict = Output(Bool())
  val branch_commit  = Output(UInt(log2Ceil(p(IssueWidth) + 1).W))
  val rob_empty      = Output(Bool())
  val issue_count    = Output(UInt(log2Ceil(p(IssueWidth) + 1).W))
  val commit_count   = Output(UInt(log2Ceil(p(IssueWidth) + 1).W))

  val frontend_stall = Output(Bool())
  val backend_stall  = Output(Bool())
}
