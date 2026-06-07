package arch.core.flush

import arch.configs._
import arch.core.exception.ExceptionRequest
import chisel3._

class FlushRobIO(implicit p: Parameters) extends Bundle {
  val flushes = Vec(p(IssueWidth), Input(Bool()))
  val targets = Vec(p(IssueWidth), Input(UInt(p(XLen).W)))
}

class FlushExceptionIO(implicit p: Parameters) extends Bundle {
  val request = Output(new ExceptionRequest)
}
