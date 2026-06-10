package arch.core.flush

import arch.configs._
import chisel3._

class FlushRobReq(implicit p: Parameters) extends Bundle {
  val flushes = Vec(p(CommitWidth), Bool())
  val targets = Vec(p(CommitWidth), UInt(p(XLen).W))
}
