package arch.core.flush

import arch.configs._
import chisel3._

class FlushRobSyncLane(implicit p: Parameters) extends Bundle {
  val valid             = Bool()
  val trap_ret          = Bool()
  val target            = UInt(p(XLen).W)
  val pc                = UInt(p(XLen).W)
  val cause             = UInt(p(XLen).W)
  val write_csr         = Bool()
  val requires_csr_idle = Bool()
}

class FlushRobReq(implicit p: Parameters) extends Bundle {
  val redirect_valid  = Vec(p(CommitWidth), Bool())
  val redirect_target = Vec(p(CommitWidth), UInt(p(XLen).W))
  val sync            = Vec(p(CommitWidth), new FlushRobSyncLane)
}
