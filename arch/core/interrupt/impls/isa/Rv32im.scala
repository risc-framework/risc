package arch.core.interrupt.impls.isa.rv32im

import arch.configs._
import arch.core.csr.{ CsrTrapView, InterruptLines }
import arch.core.exception.ExceptionRequest
import arch.core.interrupt._
import arch.core.interrupt.impls.isa.rv32i.InterruptRv32iIsa
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }

object InterruptRv32imIsa extends RegisteredNodeUtils[InterruptIsaImpl] {
  override def utils: InterruptIsaImpl = new InterruptIsaImpl {
    private val rv32i = InterruptRv32iIsa.utils

    override def value: String = "rv32im"

    override def detect(
      view: CsrTrapView,
      irq: InterruptLines
    )(implicit p: Parameters): ExceptionRequest =
      rv32i.detect(view, irq)
  }

  override def registry: NodeDimensionRegistry[InterruptIsaImpl] =
    InterruptIsaFactory
}
