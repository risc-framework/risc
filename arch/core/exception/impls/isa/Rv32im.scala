package arch.core.exception.impls.isa.rv32im

import arch.core.exception._
import arch.core.exception.impls.isa.rv32i.ExceptionRv32iIsa
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._

object ExceptionRv32imIsa extends RegisteredNodeUtils[ExceptionIsaImpl] {
  override def utils: ExceptionIsaImpl = new ExceptionIsaImpl {
    private val rv32i = ExceptionRv32iIsa.utils

    override def value: String = "rv32im"

    override def kindWidth: Int =
      rv32i.kindWidth

    override def causeWidth: Int =
      rv32i.causeWidth

    override def redirectKind: UInt =
      rv32i.redirectKind

    override def entries: Seq[ExceptionHandleEntry] =
      rv32i.entries
  }

  override def registry: NodeDimensionRegistry[ExceptionIsaImpl] =
    ExceptionIsaFactory
}
