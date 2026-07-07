package arch.core.csr

import arch.configs._
import arch.core.exception.{ ExceptionDims, ExceptionIsaFactory }
import vutils.graph.{ NodeConfig, NodeSelector }
import chisel3._

case class CsrRegister(
  name: String,
  addr: BigInt,
  initValue: BigInt = 0L,
  writable: Boolean = true,
  readable: Boolean = true
)

object CsrUpdateBehavior {
  type CsrUpdateFn = Map[String, UInt] => UInt
}

sealed trait CsrUpdateBehavior
case object NormalUpdate                                        extends CsrUpdateBehavior
case class AlwaysUpdate(fn: CsrUpdateBehavior.CsrUpdateFn)      extends CsrUpdateBehavior
case class ConditionalUpdate(fn: CsrUpdateBehavior.CsrUpdateFn) extends CsrUpdateBehavior

class CsrFileCmd(val addrWidth: Int, val opWidth: Int)(implicit p: Parameters) extends Bundle {
  private val cfg  = NodeConfig(selector = NodeSelector(CsrDims.FILE -> p(ISA).name))
  private val file = CsrFileFactory.select(cfg)

  val valid = Bool()
  val read  = Bool()
  val write = Bool()
  val addr  = UInt(file.addrWidth.W)
  val op    = UInt(file.opWidth.W)
  val data  = UInt(p(XLen).W)
}

class CsrSysCmd(implicit p: Parameters) extends Bundle {
  private val cfg = NodeConfig(selector = NodeSelector(ExceptionDims.ISA -> p(ISA).name))
  private val isa = ExceptionIsaFactory.select(cfg)

  val valid  = Bool()
  val kind   = UInt(isa.kindWidth.W)
  val target = UInt(p(XLen).W)
}

class CsrTrapView(implicit p: Parameters) extends Bundle {
  val status           = UInt(p(XLen).W)
  val interruptEnable  = UInt(p(XLen).W)
  val interruptPending = UInt(p(XLen).W)
  val trapVector       = UInt(p(XLen).W)
  val epc              = UInt(p(XLen).W)
}

class CsrCtrlReq extends Bundle {
  val cycle   = UInt(64.W)
  val instret = UInt(64.W)
}

class CsrCtrlResp(implicit p: Parameters) extends Bundle {
  val view = new CsrTrapView
  val ir   = new CsrSysCmd
  val busy = Bool()
}

class InterruptLines extends Bundle {
  val timer_irq = Bool()
  val soft_irq  = Bool()
  val ext_irq   = Bool()
}
