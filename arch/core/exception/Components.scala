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

class ExceptionIfuIO(implicit p: Parameters) extends Bundle {
  val redirect = Output(Bool())
  val target   = Output(UInt(p(XLen).W))
}

class ExceptionStoreBufferIO extends Bundle {
  val flush = Output(Bool())
}

class ExceptionSchedulerIO extends Bundle {
  val flush = Output(Bool())
}

class ExceptionFuPoolIO extends Bundle {
  val flush = Output(Bool())
}

class ExceptionRobIO extends Bundle {
  val flush = Output(Bool())
}
