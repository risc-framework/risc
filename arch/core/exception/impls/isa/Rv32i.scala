package arch.core.exception.impls.isa.rv32i

import arch.configs._
import arch.core.csr.CsrTrapUpdate
import arch.core.exception._
import arch.core.interrupt.TrapCandidate
import vutils.graph.{ NodeRegistry, RegisteredNodeUtils }
import chisel3._

object ExceptionRv32iIsa extends RegisteredNodeUtils[ExceptionIsaImpl] {
  override def utils: ExceptionIsaImpl = new ExceptionIsaImpl {
    override def value: String = "rv32i"

    override def select(
      interrupt: TrapCandidate,
      commitRedirect: RedirectBundle,
      csrBusy: Bool,
      archPc: UInt
    )(implicit p: Parameters): (RedirectBundle, CsrTrapUpdate) = {
      val redirect   = Wire(new RedirectBundle)
      val trapUpdate = Wire(new CsrTrapUpdate)

      val takeInterrupt = interrupt.valid && !csrBusy

      redirect.valid  := takeInterrupt || commitRedirect.valid
      redirect.target := Mux(takeInterrupt, interrupt.target, commitRedirect.target)

      trapUpdate.valid := takeInterrupt
      trapUpdate.pc    := archPc
      trapUpdate.cause := interrupt.cause

      (redirect, trapUpdate)
    }
  }

  override def registry: NodeRegistry[ExceptionIsaImpl] = ExceptionIsaFactory
}
