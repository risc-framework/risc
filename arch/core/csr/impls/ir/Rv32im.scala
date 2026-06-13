package arch.core.csr.impls.ir.rv32im

import arch.configs._
import arch.core.csr._
import arch.core.csr.impls.ir.rv32i.CsrRv32iIr
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._

object CsrRv32imIr extends RegisteredNodeUtils[CsrIrImpl] {
  override def utils: CsrIrImpl = new CsrIrImpl {
    private val rv32i = CsrRv32iIr.utils

    override def value: String = "rv32im"

    override def command(regs: Map[String, UInt], extra: Map[String, UInt])(implicit
      p: Parameters
    ): CsrIrCmd =
      rv32i.command(regs, extra)
  }

  override def registry: NodeDimensionRegistry[CsrIrImpl] =
    CsrIrFactory
}
