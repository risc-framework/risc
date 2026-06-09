package arch.core.exception

import arch.configs._
import arch.core.csr.CsrTrapUpdate
import chisel3._

class RedirectBundle(implicit p: Parameters) extends Bundle {
  val valid  = Bool()
  val target = UInt(p(XLen).W)
}

class ExceptionRequest(implicit p: Parameters) extends Bundle {
  val valid             = Bool()
  val target            = UInt(p(XLen).W)
  val cause             = UInt(p(XLen).W)
  val write_csr         = Bool()
  val requires_csr_idle = Bool()
}

class ExceptionFuPoolReq(implicit p: Parameters) extends Bundle {
  val flush       = Bool()
  val arch_pc     = UInt(p(XLen).W)
  val trap_update = new CsrTrapUpdate
}

class ExceptionFuPoolResp extends Bundle {
  val csr_busy = Bool()
}

class ExceptionDebugInfo(implicit p: Parameters) extends Bundle {
  val redirect = new RedirectBundle
  val arch_pc  = UInt(p(XLen).W)
}
