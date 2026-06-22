package arch.core.exception

import arch.configs._
import vutils.graph.{ NodeConfig, NodeSelector }
import chisel3._

object ExceptionSource extends ChiselEnum {
  val NONE, REDIRECT, SYNC, ASYNC = Value
}

class ExceptionRedirectReq(implicit p: Parameters) extends Bundle {
  val valid  = Bool()
  val target = UInt(p(XLen).W)
}

class ExceptionSyncReq(implicit p: Parameters) extends Bundle {
  private val cfg = NodeConfig(selector = NodeSelector(ExceptionDims.ISA -> p(ISA).name))
  private val isa = ExceptionIsaFactory.select(cfg)

  val valid  = Bool()
  val kind   = UInt(isa.kindWidth.W)
  val target = UInt(p(XLen).W)
  val pc     = UInt(p(XLen).W)
}

class ExceptionAsyncReq(implicit p: Parameters) extends Bundle {
  private val cfg = NodeConfig(selector = NodeSelector(ExceptionDims.ISA -> p(ISA).name))
  private val isa = ExceptionIsaFactory.select(cfg)

  val valid  = Bool()
  val kind   = UInt(isa.kindWidth.W)
  val target = UInt(p(XLen).W)
}

class ExceptionCsrStatus extends Bundle {
  val busy = Bool()
}

class ExceptionTrapUpdate(implicit p: Parameters) extends Bundle {
  private val cfg = NodeConfig(selector = NodeSelector(ExceptionDims.ISA -> p(ISA).name))
  private val isa = ExceptionIsaFactory.select(cfg)

  val valid  = Bool()
  val is_ret = Bool()
  val pc     = UInt(p(XLen).W)
  val kind   = UInt(isa.kindWidth.W)
  val cause  = UInt(isa.causeWidth.W)
}

class ExceptionCsrReq(implicit p: Parameters) extends Bundle {
  val flush       = Bool()
  val arch_pc     = UInt(p(XLen).W)
  val trap_update = new ExceptionTrapUpdate
}

class ExceptionRawReq(implicit p: Parameters) extends Bundle {
  private val cfg = NodeConfig(selector = NodeSelector(ExceptionDims.ISA -> p(ISA).name))
  private val isa = ExceptionIsaFactory.select(cfg)

  val valid  = Bool()
  val source = ExceptionSource()
  val kind   = UInt(isa.kindWidth.W)
  val target = UInt(p(XLen).W)
  val pc     = UInt(p(XLen).W)
}

class ExceptionHandleContext(implicit p: Parameters) extends Bundle {
  val arch_pc  = UInt(p(XLen).W)
  val csr_busy = Bool()
}

class ExceptionResolvedReq(implicit p: Parameters) extends Bundle {
  private val cfg = NodeConfig(selector = NodeSelector(ExceptionDims.ISA -> p(ISA).name))
  private val isa = ExceptionIsaFactory.select(cfg)

  val valid             = Bool()
  val source            = ExceptionSource()
  val kind              = UInt(isa.kindWidth.W)
  val target            = UInt(p(XLen).W)
  val pc                = UInt(p(XLen).W)
  val cause             = UInt(isa.causeWidth.W)
  val priority          = UInt(8.W)
  val write_csr         = Bool()
  val is_ret            = Bool()
  val requires_csr_idle = Bool()
}

class ExceptionFlushReq(implicit p: Parameters) extends Bundle {
  private val cfg = NodeConfig(selector = NodeSelector(ExceptionDims.ISA -> p(ISA).name))
  private val isa = ExceptionIsaFactory.select(cfg)

  val valid       = Bool()
  val target      = UInt(p(XLen).W)
  val source      = ExceptionSource()
  val kind        = UInt(isa.kindWidth.W)
  val cause       = UInt(isa.causeWidth.W)
  val arch_pc     = UInt(p(XLen).W)
  val trap_update = new ExceptionTrapUpdate
}
