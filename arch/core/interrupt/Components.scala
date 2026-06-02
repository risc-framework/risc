package arch.core.interrupt

import arch.configs._
import chisel3._

class TrapCandidate(implicit p: Parameters) extends Bundle {
  val valid  = Bool()
  val target = UInt(p(XLen).W)
  val cause  = UInt(p(XLen).W)
}
