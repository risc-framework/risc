package arch.core.exception

import arch.configs._
import chisel3._

class RedirectBundle(implicit p: Parameters) extends Bundle {
  val valid  = Bool()
  val target = UInt(p(XLen).W)
}

class ExceptionFlushIO(implicit p: Parameters) extends Bundle {
  val redirect = Input(Bool())
  val target   = Input(UInt(p(XLen).W))
}
