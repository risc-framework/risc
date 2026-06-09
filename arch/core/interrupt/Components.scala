package arch.core.interrupt

import arch.configs._
import arch.core.csr.{ CsrTrapView, InterruptLines }
import chisel3._

class InterruptCpuReq extends Bundle {
  val irq = new InterruptLines
}

class InterruptFuPoolResp(implicit p: Parameters) extends Bundle {
  val view = new CsrTrapView
}
