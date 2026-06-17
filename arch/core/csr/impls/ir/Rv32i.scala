package arch.core.csr.impls.ir.rv32i

import arch.configs._
import arch.core.csr._
import arch.core.exception.impls.isa.rv32i.Rv32iExceptionKindConsts
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.Cat

object Rv32iCsrIr extends RegisteredNodeUtils[CsrIrImpl] with Rv32iExceptionKindConsts {
  override def utils: CsrIrImpl = new CsrIrImpl with Rv32iExceptionKindConsts {
    override def value: String = "rv32i"

    override def command(regs: Map[String, UInt], extra: Map[String, UInt])(implicit
      p: Parameters
    ): CsrSysCmd = {
      val cmd     = Wire(new CsrSysCmd)
      val mstatus = regs.getOrElse("mstatus", 0.U(p(XLen).W))
      val mie     = regs.getOrElse("mie", 0.U(p(XLen).W))
      val mip     = regs.getOrElse("mip", 0.U(p(XLen).W))
      val mtvec   = regs.getOrElse("mtvec", 0.U(p(XLen).W))

      val globalEnable = mstatus(3)
      val meip         = globalEnable && mie(11) && mip(11)
      val msip         = globalEnable && mie(3) && mip(3)
      val mtip         = globalEnable && mie(7) && mip(7)

      cmd.valid  := mtip || msip || meip
      cmd.target := Cat(mtvec(p(XLen) - 1, 2), 0.U(2.W))
      cmd.kind   := Mux(
        mtip,
        E(E_MACHINE_TIMER_INTERRUPT),
        Mux(msip, E(E_MACHINE_SOFTWARE_INTERRUPT), E(E_MACHINE_EXTERNAL_INTERRUPT))
      )

      cmd
    }
  }

  override def registry: NodeDimensionRegistry[CsrIrImpl] =
    CsrIrFactory
}
