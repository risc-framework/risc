package arch.core.ifu

import arch.configs._
import chisel3._

class IfuExceptionReq(implicit p: Parameters) extends Bundle {
  val redirect = Bool()
  val target   = UInt(p(XLen).W)
}

class IfuExceptionResp(implicit p: Parameters) extends Bundle {
  val fetch_pc = UInt(p(XLen).W)
}
