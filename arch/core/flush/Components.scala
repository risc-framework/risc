package arch.core.flush

import arch.configs._
import arch.core.exception.{ ExceptionDims, ExceptionIsaFactory }
import vutils.graph.{ NodeConfig, NodeSelector }
import chisel3._

class FlushRobSyncLane(implicit p: Parameters) extends Bundle {
  private val cfg = NodeConfig(selector = NodeSelector(ExceptionDims.ISA -> p(ISA).name))
  private val isa = ExceptionIsaFactory.select(cfg)

  val valid             = Bool()
  val kind              = UInt(isa.kindWidth.W)
  val target            = UInt(p(XLen).W)
  val pc                = UInt(p(XLen).W)
  val requires_csr_idle = Bool()
}

class FlushRobReq(implicit p: Parameters) extends Bundle {
  val redirect_valid  = Vec(p(CommitWidth), Bool())
  val redirect_target = Vec(p(CommitWidth), UInt(p(XLen).W))
  val sync            = Vec(p(CommitWidth), new FlushRobSyncLane)
}
