package arch.core.ifu

import arch.configs._
import chisel3._

class IfuExceptionResp(implicit p: Parameters) extends Bundle {
  val fetch_pc = UInt(p(XLen).W)
}
