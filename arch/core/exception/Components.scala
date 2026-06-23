package arch.core.exception

import arch.configs._
import vutils.graph.{ NodeConfig, NodeSelector }
import chisel3._

object ExceptionSource extends ChiselEnum {
  val NONE, REDIRECT, SYNC, ASYNC = Value
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
  val pc     = UInt(p(XLen).W)
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
