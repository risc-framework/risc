package arch.core.csr.impls.ir.rv32im

import arch.core.csr._
import arch.core.csr.impls.ir.rv32i.Rv32iCsrIr
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }

object Rv32imCsrIr extends RegisteredNodeUtils[CsrIrImpl] {
  override def utils: CsrIrImpl = new CsrIrImpl {
    private val rv32i = Rv32iCsrIr.utils

    override def value: String =
      "rv32im"

    override def command(regs: Map[String, chisel3.UInt], extra: Map[String, chisel3.UInt])(implicit
      p: arch.configs.Parameters
    ): CsrIrCmd =
      rv32i.command(regs, extra)
  }

  override def registry: NodeDimensionRegistry[CsrIrImpl] =
    CsrIrFactory
}
