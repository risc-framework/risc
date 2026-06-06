package arch.core.rob

import arch.core.bpu.BpuUpdate
import arch.core.dispatch.DispatchRobPacket
import arch.configs._
import chisel3._
import chisel3.util.{ log2Ceil, Valid }

class RobDispatchLaneIO(implicit p: Parameters) extends Bundle {
  val req_valid = Input(Bool())
  val req_ready = Output(Bool())
  val req_bits  = Input(new DispatchRobPacket)

  val rob_tag = Output(UInt(p(RobTagWidth).W))

  val rs1_bypass_valid   = Output(Bool())
  val rs1_bypass_data    = Output(UInt(p(XLen).W))
  val rs1_bypass_pending = Output(Bool())

  val rs2_bypass_valid   = Output(Bool())
  val rs2_bypass_data    = Output(UInt(p(XLen).W))
  val rs2_bypass_pending = Output(Bool())
}

class RobDispatchIO(implicit p: Parameters) extends Bundle {
  val lanes = Vec(p(IssueWidth), new RobDispatchLaneIO)
}

class RobRegfileWriteBundle(implicit p: Parameters) extends Bundle {
  val addr = UInt(log2Ceil(p(NumArchRegs)).W)
  val data = UInt(p(XLen).W)
}

class RobRegfileIO(implicit p: Parameters) extends Bundle {
  val write = Output(Vec(p(IssueWidth), Valid(new RobRegfileWriteBundle)))
}

class RobSbCommitBundle(implicit p: Parameters) extends Bundle {
  val is_store = Bool()
  val sq_idx   = UInt(log2Ceil(p(StoreBufferSize)).W)
}

class RobSbIO(implicit p: Parameters) extends Bundle {
  val commit = Output(Vec(p(IssueWidth), Valid(new RobSbCommitBundle)))
}

class RobCommitInfo(implicit p: Parameters) extends Bundle {
  val valid             = Bool()
  val pop               = Bool()
  val pc                = UInt(p(XLen).W)
  val instr             = UInt(p(ILen).W)
  val rd                = UInt(log2Ceil(p(NumArchRegs)).W)
  val rd_write          = Bool()
  val data              = UInt(p(XLen).W)
  val flush_pipeline    = Bool()
  val flush_target      = UInt(p(XLen).W)
  val is_branch         = Bool()
  val is_store          = Bool()
  val commit_barrier    = Bool()
  val bpu_pred_taken    = Bool()
  val bpu_pred_target   = UInt(p(XLen).W)
  val bpu_actual_taken  = Bool()
  val bpu_actual_target = UInt(p(XLen).W)
  val bpu_pht_index     = UInt(p(GShareGhrWidth).W)
  val bpu_ghr_snapshot  = UInt(p(GShareGhrWidth).W)
  val sq_idx            = UInt(log2Ceil(p(StoreBufferSize)).W)
}

class RobBpuIO(implicit p: Parameters) extends Bundle {
  val update = Output(new BpuUpdate)
}

class RobExceptionIO(implicit p: Parameters) extends Bundle {
  val flush     = Input(Bool())
  val empty     = Output(Bool())
  val commit_pc = Output(UInt(p(XLen).W))
}

class RobDebugIO(implicit p: Parameters) extends Bundle {
  val instret        = Output(Vec(p(IssueWidth), Bool()))
  val pc             = Output(Vec(p(IssueWidth), UInt(p(XLen).W)))
  val instr          = Output(Vec(p(IssueWidth), UInt(p(ILen).W)))
  val reg_we         = Output(Vec(p(IssueWidth), Bool()))
  val reg_addr       = Output(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
  val reg_data       = Output(Vec(p(IssueWidth), UInt(p(XLen).W)))
  val commit_count   = Output(UInt(log2Ceil(p(IssueWidth) + 1).W))
  val branch_commit  = Output(UInt(log2Ceil(p(IssueWidth) + 1).W))
  val bpu_mispredict = Output(Bool())
  val empty          = Output(Bool())
}

class RobEntry(implicit p: Parameters) extends Bundle {
  val valid          = Bool()
  val ready          = Bool()
  val pc             = UInt(p(XLen).W)
  val instr          = UInt(p(ILen).W)
  val rd             = UInt(log2Ceil(p(NumArchRegs)).W)
  val rd_write       = Bool()
  val data           = UInt(p(XLen).W)
  val is_branch      = Bool()
  val is_store       = Bool()
  val commit_barrier = Bool()
  val pred_taken     = Bool()
  val pred_target    = UInt(p(XLen).W)
  val pht_index      = UInt(p(GShareGhrWidth).W)
  val ghr_snapshot   = UInt(p(GShareGhrWidth).W)
  val actual_taken   = Bool()
  val actual_target  = UInt(p(XLen).W)
  val flush_pipeline = Bool()
  val flush_target   = UInt(p(XLen).W)
  val sq_idx         = UInt(log2Ceil(p(StoreBufferSize)).W)
}

class RobFlushIO(implicit p: Parameters) extends Bundle {
  val flushes = Output(Vec(p(IssueWidth), Bool()))
  val targets = Output(Vec(p(IssueWidth), UInt(p(XLen).W)))
}
