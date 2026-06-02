package arch.core.interrupt.impls.isa.rv32i

import arch.configs._
import arch.core.csr.{ CsrTrapView, InterruptLines }
import arch.core.interrupt._
import vutils.graph.{ NodeRegistry, RegisteredNodeUtils }
import chisel3._

object InterruptRv32iIsa extends RegisteredNodeUtils[InterruptIsaImpl] {
  override def utils: InterruptIsaImpl = new InterruptIsaImpl {
    override def value: String = "rv32i"

    override def detect(view: CsrTrapView, irq: InterruptLines)(implicit
      p: Parameters
    ): TrapCandidate = {
      val out = Wire(new TrapCandidate)

      val globalEnable = view.status(3)

      val extPending = irq.ext_irq || view.interruptPending(11)
      val timPending = irq.timer_irq || view.interruptPending(7)
      val sftPending = irq.soft_irq || view.interruptPending(3)

      val takeExt = globalEnable && extPending && view.interruptEnable(11)
      val takeSft = globalEnable && sftPending && view.interruptEnable(3)
      val takeTim = globalEnable && timPending && view.interruptEnable(7)

      val asyncBit = 1.U(p(XLen).W) << (p(XLen) - 1)

      out.valid  := takeExt || takeSft || takeTim
      out.target := view.trapVector
      out.cause  := Mux(
        takeExt,
        asyncBit | 11.U,
        Mux(takeSft, asyncBit | 3.U, Mux(takeTim, asyncBit | 7.U, 0.U))
      )

      out
    }
  }

  override def registry: NodeRegistry[InterruptIsaImpl] = InterruptIsaFactory
}
