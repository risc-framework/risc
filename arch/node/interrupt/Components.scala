package arch.node.interrupt

import arch.configs._
import chisel3._

class InterruptLines extends Bundle {
  val timer_irq = Bool()
  val soft_irq  = Bool()
  val ext_irq   = Bool()
}

class InterruptCsrView(implicit p: Parameters) extends Bundle {
  val mstatus = UInt(p(XLen).W)
  val mie     = UInt(p(XLen).W)
  val mip     = UInt(p(XLen).W)
  val mtvec   = UInt(p(XLen).W)
}

class InterruptTrap(implicit p: Parameters) extends Bundle {
  val valid  = Bool()
  val target = UInt(p(XLen).W)
  val cause  = UInt(p(XLen).W)
}

class InterruptIO(implicit p: Parameters) extends Bundle {
  val csr = Input(new InterruptCsrView)
  val irq = Input(new InterruptLines)
  val out = Output(new InterruptTrap)
}
