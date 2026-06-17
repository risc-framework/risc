package arch.core.ifu

import arch.configs._
import chisel3._
import chisel3.util.log2Ceil

class IBufferEntry(implicit p: Parameters) extends Bundle {
  val pc               = UInt(p(XLen).W)
  val instr            = UInt(p(ILen).W)
  val bpu_pred_taken   = Bool()
  val bpu_pred_target  = UInt(p(XLen).W)
  val bpu_pht_index    = UInt(p(GShareGhrWidth).W)
  val bpu_ghr_snapshot = UInt(p(GShareGhrWidth).W)
}

class IBufferFlush extends Bundle {
  val flush = Bool()
}

class IBufferStatus(implicit p: Parameters) extends Bundle {
  val enq_ready = Bool()
  val empty     = Bool()
  val full      = Bool()
  val count     = UInt(log2Ceil(p(IBufferSize) + 1).W)
}

class IfuExceptionReq(implicit p: Parameters) extends Bundle {
  val redirect = Bool()
  val target   = UInt(p(XLen).W)
}

class IfuExceptionResp(implicit p: Parameters) extends Bundle {
  val fetch_pc = UInt(p(XLen).W)
}

class IfuDebugInfo extends Bundle {
  val ibuffer_full = Bool()
}
