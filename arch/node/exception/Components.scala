package arch.node.exception

import arch.configs._
import chisel3._

class RedirectBundle(implicit p: Parameters) extends Bundle {
  val valid  = Bool()
  val target = UInt(p(XLen).W)
}
