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

class CsrFileCmd(val addrWidth: Int, val opWidth: Int)(implicit p: Parameters) extends Bundle {
  val valid = Bool()
  val read  = Bool()
  val write = Bool()
  val addr  = UInt(addrWidth.W)
  val op    = UInt(opWidth.W)
  val data  = UInt(p(XLen).W)
}

class CsrSyncCmd(implicit p: Parameters) extends Bundle {
  val trap_ret       = Bool()
  val sync_exception = Bool()
  val cause          = UInt(p(XLen).W)
}

class CsrIrCmd(implicit p: Parameters) extends Bundle {
  val valid  = Bool()
  val target = UInt(p(XLen).W)
  val cause  = UInt(p(XLen).W)
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
  val valid  = Bool()
  val is_ret = Bool()
  val pc     = UInt(p(XLen).W)
  val cause  = UInt(p(XLen).W)
}

class CsrCtrlReq(implicit p: Parameters) extends Bundle {
  val cycle       = UInt(64.W)
  val instret     = UInt(64.W)
  val irq         = new InterruptLines
  val arch_pc     = UInt(p(XLen).W)
  val trap_update = new CsrTrapUpdate
}

class CsrCtrlResp(implicit p: Parameters) extends Bundle {
  val view = new CsrTrapView
  val ir   = new CsrIrCmd
  val busy = Bool()
}
