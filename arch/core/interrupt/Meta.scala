package arch.core.interrupt

import arch.configs._
import arch.core.csr.{ CsrTrapView, InterruptLines }
import arch.core.exception.ExceptionRequest
import vutils.graph.NodeDims

object InterruptDims extends NodeDims("interrupt") {
  val ISA = dim("isa")
}

trait InterruptIsaImpl extends InterruptDims.ISA.Impl {
  def detect(
    view: CsrTrapView,
    irq: InterruptLines
  )(implicit p: Parameters): ExceptionRequest
}

object InterruptIsaFactory extends InterruptDims.ISA.Registry[InterruptIsaImpl]

object InterruptInit {
  val rv32i  = impls.isa.rv32i.InterruptRv32iIsa.registered
  val rv32im = impls.isa.rv32im.InterruptRv32imIsa.registered
}
