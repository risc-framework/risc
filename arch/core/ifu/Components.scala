package arch.core.ifu

import arch.configs._
import chisel3._

class RedirectInfo(implicit p: Parameters) extends Bundle {
  val valid  = Bool()
  val target = UInt(p(XLen).W)
}
