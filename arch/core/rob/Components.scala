package arch.core.rob

import arch.core.bpu.BpuUpdate
import arch.core.decode.DecodedPacket
import arch.core.dispatch.DispatchRobPacket
import arch.configs._
import chisel3._
import chisel3.util.{ Decoupled, log2Ceil }

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

class RobEnqPacket(implicit p: Parameters) extends Bundle {
  val decoded = new DecodedPacket
  val sq_idx  = UInt(log2Ceil(p(StoreBufferSize)).W)
}

class RobEnqIO(implicit p: Parameters) extends Bundle {
  val req     = Decoupled(new RobEnqPacket)
  val rob_tag = Input(UInt(p(RobTagWidth).W))
}

class RobCommitIO(implicit p: Parameters) extends Bundle {
  val valid             = Output(Bool())
  val pop               = Input(Bool())
  val pc                = Output(UInt(p(XLen).W))
  val instr             = Output(UInt(p(ILen).W))
  val rd                = Output(UInt(log2Ceil(p(NumArchRegs)).W))
  val rd_write          = Output(Bool())
  val data              = Output(UInt(p(XLen).W))
  val flush_pipeline    = Output(Bool())
  val flush_target      = Output(UInt(p(XLen).W))
  val is_branch         = Output(Bool())
  val is_store          = Output(Bool())
  val commit_barrier    = Output(Bool())
  val bpu_pred_taken    = Output(Bool())
  val bpu_pred_target   = Output(UInt(p(XLen).W))
  val bpu_actual_taken  = Output(Bool())
  val bpu_actual_target = Output(UInt(p(XLen).W))
  val bpu_pht_index     = Output(UInt(p(GShareGhrWidth).W))
  val bpu_ghr_snapshot  = Output(UInt(p(GShareGhrWidth).W))
  val sq_idx            = Output(UInt(log2Ceil(p(StoreBufferSize)).W))
}

class RobBypassResp(implicit p: Parameters) extends Bundle {
  val valid   = Output(Bool())
  val data    = Output(UInt(p(XLen).W))
  val pending = Output(Bool())
}

class RobCommitPortIO(implicit p: Parameters) extends Bundle {
  val lanes = Vec(p(IssueWidth), new RobCommitIO)
}

class RobBpuIO(implicit p: Parameters) extends Bundle {
  val update = Output(new BpuUpdate)
}

class RobCtrlIO extends Bundle {
  val empty = Output(Bool())
}

class RobExceptionIO extends Bundle {
  val flush = Input(Bool())
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
