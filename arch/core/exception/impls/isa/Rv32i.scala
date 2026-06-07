package arch.core.exception.impls.isa.rv32i

import arch.configs._
import arch.core.csr.CsrTrapUpdate
import arch.core.exception._
import vutils.graph.{ NodeRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.{ Mux1H, PriorityEncoderOH }

object ExceptionRv32iIsa extends RegisteredNodeUtils[ExceptionIsaImpl] {
  override def utils: ExceptionIsaImpl = new ExceptionIsaImpl {
    override def value: String = "rv32i"

    override def select(
      requests: Seq[ExceptionRequest],
      csrBusy: Bool,
      archPc: UInt
    )(implicit p: Parameters): (RedirectBundle, CsrTrapUpdate) = {
      val redirect   = Wire(new RedirectBundle)
      val trapUpdate = Wire(new CsrTrapUpdate)

      if (requests.isEmpty) {
        redirect.valid   := false.B
        redirect.target  := 0.U
        trapUpdate.valid := false.B
        trapUpdate.pc    := 0.U
        trapUpdate.cause := 0.U
      } else {
        val allowed = requests.map(req => req.valid && !(req.requires_csr_idle && csrBusy))
        val chosen  = PriorityEncoderOH(VecInit(allowed))

        val hasRequest = allowed.reduce(_ || _)
        val target     = Mux1H(chosen, requests.map(_.target))
        val cause      = Mux1H(chosen, requests.map(_.cause))
        val writeCsr   = Mux1H(chosen, requests.map(_.write_csr))

        redirect.valid   := hasRequest
        redirect.target  := target
        trapUpdate.valid := hasRequest && writeCsr
        trapUpdate.pc    := archPc
        trapUpdate.cause := cause
      }

      (redirect, trapUpdate)
    }
  }

  override def registry: NodeRegistry[ExceptionIsaImpl] = ExceptionIsaFactory
}
