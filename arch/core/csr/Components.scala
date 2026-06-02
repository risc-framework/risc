package arch.core.csr

import arch.configs._
import chisel3._

case class CsrRegister(
  name: String,
  addr: BigInt,
  initValue: BigInt = 0L,
  writable: Boolean = true,
  readable: Boolean = true,
)

object CsrUpdateBehavior {
  type CsrUpdateFn = Map[String, UInt] => UInt
}

sealed trait CsrUpdateBehavior
case object NormalUpdate                                        extends CsrUpdateBehavior
case class AlwaysUpdate(fn: CsrUpdateBehavior.CsrUpdateFn)      extends CsrUpdateBehavior
case class ConditionalUpdate(fn: CsrUpdateBehavior.CsrUpdateFn) extends CsrUpdateBehavior

class CsrCtrl(val opWidth: Int) extends Bundle {
  val is_sys = Bool()
  val is_imm = Bool()
  val op     = UInt(opWidth.W)
}

class InterruptLines extends Bundle {
  val timer_irq = Bool()
  val soft_irq  = Bool()
  val ext_irq   = Bool()
}

class CsrTrapView(implicit p: Parameters) extends Bundle {
  val status           = UInt(p(XLen).W)
  val interruptEnable  = UInt(p(XLen).W)
  val interruptPending = UInt(p(XLen).W)
  val trapVector       = UInt(p(XLen).W)
  val epc              = UInt(p(XLen).W)
}

class CsrTrapUpdate(implicit p: Parameters) extends Bundle {
  val valid = Bool()
  val pc    = UInt(p(XLen).W)
  val cause = UInt(p(XLen).W)
}

class CsrExtraIO extends Bundle {
  val cycle   = UInt(64.W)
  val instret = UInt(64.W)
  val irq     = new InterruptLines
}

class CsrCtrlIO(implicit p: Parameters) extends Bundle {
  val cycle       = Input(UInt(64.W))
  val instret     = Input(UInt(64.W))
  val irq         = Input(new InterruptLines)
  val arch_pc     = Input(UInt(p(XLen).W))
  val trap_update = Input(new CsrTrapUpdate)

  val view = Output(new CsrTrapView)
  val busy = Output(Bool())
}
