package arch.core.exception

import arch.configs._
import arch.core.csr.CsrTrapUpdate
import chisel3._

class ExceptionRedirectReq(implicit p: Parameters) extends Bundle {
  val valid  = Bool()
  val target = UInt(p(XLen).W)
}

class ExceptionSyncReq(implicit p: Parameters) extends Bundle {
  val valid             = Bool()
  val trap_ret          = Bool()
  val target            = UInt(p(XLen).W)
  val pc                = UInt(p(XLen).W)
  val cause             = UInt(p(XLen).W)
  val write_csr         = Bool()
  val requires_csr_idle = Bool()
}

class ExceptionAsyncReq(implicit p: Parameters) extends Bundle {
  val valid             = Bool()
  val target            = UInt(p(XLen).W)
  val cause             = UInt(p(XLen).W)
  val write_csr         = Bool()
  val requires_csr_idle = Bool()
}

class ExceptionCsrReq(implicit p: Parameters) extends Bundle {
  val flush       = Bool()
  val arch_pc     = UInt(p(XLen).W)
  val trap_update = new CsrTrapUpdate
}

class ExceptionCsrStatus extends Bundle {
  val busy = Bool()
}

class ExceptionDebugInfo(implicit p: Parameters) extends Bundle {
  val redirect_valid  = Bool()
  val redirect_target = UInt(p(XLen).W)
  val sync_valid      = Bool()
  val async_valid     = Bool()
  val arch_pc         = UInt(p(XLen).W)
  val cause           = UInt(p(XLen).W)
}
