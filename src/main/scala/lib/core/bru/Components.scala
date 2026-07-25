package arch.core.bru

import arch.configs._
import chisel3._

class BruCtrl(val opWidth: Int) extends Bundle {
  val is_jump = Bool()
  val is_jalr = Bool()
  val op      = UInt(opWidth.W)
}

class BruResolveBundle(implicit p: Parameters) extends Bundle {
  val pc          = UInt(p(XLen).W)
  val instr       = UInt(p(ILen).W)
  val rob_tag     = UInt(p(RobTagWidth).W)
  val taken       = Bool()
  val target      = UInt(p(XLen).W)
  val fallthrough = UInt(p(XLen).W)
}
