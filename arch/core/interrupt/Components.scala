package arch.core.interrupt

import arch.configs._
import arch.core.csr.InterruptLines
import arch.core.exception.ExceptionRequest
import chisel3._

class TrapCandidate(implicit p: Parameters) extends ExceptionRequest

class InterruptCpuIO extends Bundle {
  val irq = Input(new InterruptLines)
}

class InterruptExceptionIO(implicit p: Parameters) extends Bundle {
  val request = Output(new TrapCandidate)
}
