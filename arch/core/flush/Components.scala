package arch.core.flush

import arch.configs._
import arch.core.exception.ExceptionRequest
import chisel3._

class FlushRobReq(implicit p: Parameters) extends Bundle {
  val flushes = Vec(p(IssueWidth), Bool())
  val targets = Vec(p(IssueWidth), UInt(p(XLen).W))
}
