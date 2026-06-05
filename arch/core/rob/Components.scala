package arch.core.rob

import arch.core.bru.BruResolveIO
import arch.configs._
import chisel3._
import chisel3.util.log2Ceil

class RobEnqIO(implicit p: Parameters) extends Bundle {
  val valid            = Input(Bool())
  val ready            = Output(Bool())
  val pc               = Input(UInt(p(XLen).W))
  val instr            = Input(UInt(p(ILen).W))
  val rd               = Input(UInt(log2Ceil(p(NumArchRegs)).W))
  val rd_write         = Input(Bool())
  val is_branch        = Input(Bool())
  val is_store         = Input(Bool())
  val commit_barrier   = Input(Bool())
  val bpu_pred_taken   = Input(Bool())
  val bpu_pred_target  = Input(UInt(p(XLen).W))
  val bpu_pht_index    = Input(UInt(p(GShareGhrWidth).W))
  val bpu_ghr_snapshot = Input(UInt(p(GShareGhrWidth).W))
  val rob_tag          = Output(UInt(p(RobTagWidth).W))
  val sq_idx           = Input(UInt(log2Ceil(p(StoreBufferSize)).W))
}

class RobWbIO(implicit p: Parameters) extends Bundle {
  val valid   = Input(Bool())
  val rob_tag = Input(UInt(p(RobTagWidth).W))
  val data    = Input(UInt(p(XLen).W))
}

class RobTrapBundle(implicit p: Parameters) extends Bundle {
  val rob_tag      = UInt(p(RobTagWidth).W)
  val trap_req     = Bool()
  val trap_target  = UInt(p(XLen).W)
  val trap_ret     = Bool()
  val trap_ret_tgt = UInt(p(XLen).W)
}

class RobTrapIO(implicit p: Parameters) extends Bundle {
  val valid = Input(Bool())
  val bits  = Input(new RobTrapBundle)
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

class RobEnqPortIO(implicit p: Parameters) extends Bundle {
  val lanes = Vec(p(IssueWidth), new RobEnqIO)
}

class RobWbPortIO(implicit p: Parameters) extends Bundle {
  val ports = Vec(p(NumFUs), new RobWbIO)
}

class RobBruPortIO(implicit p: Parameters) extends Bundle {
  val ports = Flipped(Vec(p(NumBRUs), new BruResolveIO))
}

class RobTrapPortIO(implicit p: Parameters) extends Bundle {
  val ports = Vec(p(NumFUs), new RobTrapIO)
}

class RobCommitPortIO(implicit p: Parameters) extends Bundle {
  val lanes = Vec(p(IssueWidth), new RobCommitIO)
}

class RobBypassIO(implicit p: Parameters) extends Bundle {
  val rs1_addr   = Input(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
  val rs1_bypass = Vec(p(IssueWidth), new RobBypassResp)
  val rs2_addr   = Input(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
  val rs2_bypass = Vec(p(IssueWidth), new RobBypassResp)
}

class RobCtrlIO extends Bundle {
  val flush = Input(Bool())
  val empty = Output(Bool())
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
