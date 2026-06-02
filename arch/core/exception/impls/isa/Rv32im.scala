package arch.core.exception.impls.isa.rv32im

import arch.configs._
import arch.core.csr.CsrTrapUpdate
import arch.core.exception._
import arch.core.exception.impls.isa.rv32i.ExceptionRv32iIsa
import arch.core.interrupt.TrapCandidate
import chisel3._
import vutils.graph.{ NodeRegistry, RegisteredNodeUtils }

object ExceptionRv32imIsa extends RegisteredNodeUtils[ExceptionIsaImpl] {
  override def utils: ExceptionIsaImpl = new ExceptionIsaImpl {
    private val rv32i = ExceptionRv32iIsa.utils

    override def value: String = "rv32im"

    override def select(
      interrupt: TrapCandidate,
      commitRedirect: RedirectBundle,
      csrBusy: Bool,
      archPc: UInt
    )(implicit p: Parameters): (RedirectBundle, CsrTrapUpdate) =
      rv32i.select(interrupt, commitRedirect, csrBusy, archPc)
  }

  override def registry: NodeRegistry[ExceptionIsaImpl] = ExceptionIsaFactory
}
