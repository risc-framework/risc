package arch.core.ifu

import arch.configs._
import vcache.CacheReadOnlyPortIO
import chisel3._
import chisel3.util.Decoupled

class IBufferEntry(implicit p: Parameters) extends Bundle {
  val pc               = UInt(p(XLen).W)
  val instr            = UInt(p(ILen).W)
  val bpu_pred_taken   = Bool()
  val bpu_pred_target  = UInt(p(XLen).W)
  val bpu_pht_index    = UInt(p(GShareGhrWidth).W)
  val bpu_ghr_snapshot = UInt(p(GShareGhrWidth).W)
}

class IfuExceptionIO(implicit p: Parameters) extends Bundle {
  val redirect = Input(Bool())
  val target   = Input(UInt(p(XLen).W))
}

class IfuDispatchIO(implicit p: Parameters) extends Bundle {
  val out            = Vec(p(IssueWidth), Decoupled(new IBufferEntry))
  val fetch_pc       = Output(UInt(p(XLen).W))
  val fetch_fire     = Output(Bool())
  val frontend_flush = Output(Bool())
  val reset_ibuffer  = Output(Bool())
}

class IfuMemIO(implicit p: Parameters) extends Bundle {
  val mem = new CacheReadOnlyPortIO(Vec(p(IssueWidth), UInt(p(ILen).W)), p(L1ICacheParams))
}
