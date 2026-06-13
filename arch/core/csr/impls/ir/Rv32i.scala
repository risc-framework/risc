package arch.core.csr.impls.ir.rv32i

import arch.configs._
import arch.core.csr._
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.Cat

object CsrRv32iIr extends RegisteredNodeUtils[CsrIrImpl] {
  override def utils: CsrIrImpl = new CsrIrImpl {
    override def value: String = "rv32i"

    private def get(regs: Map[String, UInt], name: String)(implicit p: Parameters): UInt =
      regs.getOrElse(name, 0.U(p(XLen).W))

    override def command(regs: Map[String, UInt], extra: Map[String, UInt])(implicit
      p: Parameters
    ): CsrIrCmd = {
      val cmd     = Wire(new CsrIrCmd)
      val mstatus = get(regs, "mstatus")
      val mie     = get(regs, "mie")
      val mip     = get(regs, "mip")
      val mtvec   = get(regs, "mtvec")
      val global  = mstatus(3)

      val msip  = global && mie(3) && (mip(3) || extra("soft_irq").orR)
      val mtip  = global && mie(7) && (mip(7) || extra("timer_irq").orR)
      val meip  = global && mie(11) && (mip(11) || extra("ext_irq").orR)
      val async = 1.U(p(XLen).W) << (p(XLen) - 1)

      cmd.valid  := meip || msip || mtip
      cmd.target := Cat(mtvec(p(XLen) - 1, 2), 0.U(2.W))
      cmd.cause  := Mux(
        meip,
        async | 11.U(p(XLen).W),
        Mux(msip, async | 3.U(p(XLen).W), async | 7.U(p(XLen).W))
      )

      cmd
    }
  }

  override def registry: NodeDimensionRegistry[CsrIrImpl] =
    CsrIrFactory
}
