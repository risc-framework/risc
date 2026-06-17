package arch.core.cpu

import arch.configs._
import vcache.{ CacheReq, CacheResp }
import chisel3._
import chisel3.util.log2Ceil

class CpuImemReq(implicit p: Parameters)
    extends CacheReq(Vec(p(IssueWidth), UInt(p(ILen).W)), p(L1ICacheParams))

class CpuImemResp(implicit p: Parameters)
    extends CacheResp(Vec(p(IssueWidth), UInt(p(ILen).W)), p(L1ICacheParams))

class CpuDmemReq(implicit p: Parameters) extends CacheReq(UInt(p(XLen).W), p(L1DCacheParams))

class CpuDmemResp(implicit p: Parameters) extends CacheResp(UInt(p(XLen).W), p(L1DCacheParams))

class CpuDebugInfo(implicit p: Parameters) extends Bundle {
  val cycle_count   = UInt(64.W)
  val instret_count = UInt(64.W)

  val instret  = Vec(p(CommitWidth), Bool())
  val pc       = Vec(p(CommitWidth), UInt(p(XLen).W))
  val instr    = Vec(p(CommitWidth), UInt(p(ILen).W))
  val reg_we   = Vec(p(CommitWidth), Bool())
  val reg_addr = Vec(p(CommitWidth), UInt(log2Ceil(p(NumArchRegs)).W))
  val reg_data = Vec(p(CommitWidth), UInt(p(XLen).W))

  val branch_taken  = Bool()
  val branch_source = UInt(p(XLen).W)
  val branch_target = UInt(p(XLen).W)

  val l1_icache_access = Bool()
  val l1_icache_miss   = Bool()
  val l1_dcache_access = Bool()
  val l1_dcache_miss   = Bool()

  val flush_cycle    = Bool()
  val bpu_mispredict = Bool()
  val branch_commit  = UInt(log2Ceil(p(CommitWidth) + 1).W)
  val rob_empty      = Bool()
  val issue_count    = UInt(log2Ceil(p(CommitWidth) + 1).W)
  val commit_count   = UInt(log2Ceil(p(CommitWidth) + 1).W)

  val frontend_stall = Bool()
  val backend_stall  = Bool()

  val stall_if_redirect        = Bool()
  val stall_if_ras_wait        = Bool()
  val stall_ibuffer_full       = Bool()
  val stall_decode_not_ready   = Bool()
  val stall_dispatch_not_ready = Bool()
  val stall_rob_full           = Bool()
  val stall_issue_queue_full   = Bool()
  val stall_lsq_full           = Bool()
  val stall_flush_recovery     = Bool()
}
