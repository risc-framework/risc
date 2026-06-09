package arch.core.exception.impls.isa.rv32im

import arch.configs._
import arch.core.csr.CsrTrapUpdate
import arch.core.exception._
import arch.core.exception.impls.isa.rv32i.ExceptionRv32iIsa
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._

object ExceptionRv32imIsa extends RegisteredNodeUtils[ExceptionIsaImpl] {
  override def utils: ExceptionIsaImpl = new ExceptionIsaImpl {
    private val rv32i = ExceptionRv32iIsa.utils

    override def value: String = "rv32im"

    override def select(
      requests: Seq[ExceptionRequest],
      csrBusy: Bool,
      archPc: UInt
    )(implicit p: Parameters): (RedirectBundle, CsrTrapUpdate) =
      rv32i.select(requests, csrBusy, archPc)
  }

  override def registry: NodeDimensionRegistry[ExceptionIsaImpl] =
    ExceptionIsaFactory
}
