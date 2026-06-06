package arch.core.ifu

import arch.configs._
import vcache.CacheReadOnlyPortIO
import chisel3._

class IBufferEntry(implicit p: Parameters) extends Bundle {
  val pc               = UInt(p(XLen).W)
  val instr            = UInt(p(ILen).W)
  val bpu_pred_taken   = Bool()
  val bpu_pred_target  = UInt(p(XLen).W)
  val bpu_pht_index    = UInt(p(GShareGhrWidth).W)
  val bpu_ghr_snapshot = UInt(p(GShareGhrWidth).W)
}

class IfuBpuIO(implicit p: Parameters) extends Bundle {
  val query_pc      = Output(Vec(p(IssueWidth), UInt(p(XLen).W)))
  val advance_valid = Output(Bool())
  val flush         = Output(Bool())
  val taken         = Input(Vec(p(IssueWidth), Bool()))
  val target        = Input(Vec(p(IssueWidth), UInt(p(XLen).W)))
  val pht_index     = Input(Vec(p(IssueWidth), UInt(p(GShareGhrWidth).W)))
  val ghr_snapshot  = Input(Vec(p(IssueWidth), UInt(p(GShareGhrWidth).W)))
}

class IfuDecodeLaneIO(implicit p: Parameters) extends Bundle {
  val valid = Output(Bool())
  val bits  = Output(new IBufferEntry)
  val ready = Input(Bool())
}

class IfuDecodeIO(implicit p: Parameters) extends Bundle {
  val lanes = Vec(p(IssueWidth), new IfuDecodeLaneIO)
}

class IfuExceptionIO(implicit p: Parameters) extends Bundle {
  val redirect       = Input(Bool())
  val target         = Input(UInt(p(XLen).W))
  val fetch_pc       = Output(UInt(p(XLen).W))
  val fetch_fire     = Output(Bool())
  val frontend_flush = Output(Bool())
  val reset_ibuffer  = Output(Bool())
}

class IfuICacheIO(implicit p: Parameters) extends Bundle {
  val mem = new CacheReadOnlyPortIO(Vec(p(IssueWidth), UInt(p(ILen).W)), p(L1ICacheParams))
}
