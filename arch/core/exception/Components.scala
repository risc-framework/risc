package arch.core.exception

import arch.configs._
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

class ExceptionDispatchIO extends Bundle {
  val flush = Output(Bool())
}

class ExceptionStoreBufferIO extends Bundle {
  val flush = Output(Bool())
}

class ExceptionSchedulerIO extends Bundle {
  val flush = Output(Bool())
}

class ExceptionDebugIO(implicit p: Parameters) extends Bundle {
  val redirect = Output(new RedirectBundle)
  val arch_pc  = Output(UInt(p(XLen).W))
}
