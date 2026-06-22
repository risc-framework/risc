package arch.core.exception.impls.isa.rv32im

import arch.core.exception._
import arch.core.exception.impls.isa.rv32i.ExceptionRv32iIsa
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }

object ExceptionRv32imIsa extends RegisteredNodeUtils[ExceptionIsaImpl] {
  override def utils: ExceptionIsaImpl = new ExceptionIsaImpl {
    private val rv32i = ExceptionRv32iIsa.utils

    override def value: String = "rv32im"

    override def kindWidth: Int  = rv32i.kindWidth
    override def causeWidth: Int = rv32i.causeWidth

    override def redirectEntries: Seq[ExceptionRedirectEntry] = rv32i.redirectEntries
    override def syncEntries: Seq[ExceptionSyncEntry]         = rv32i.syncEntries
    override def asyncEntries: Seq[ExceptionAsyncEntry]       = rv32i.asyncEntries
  }

  override def registry: NodeDimensionRegistry[ExceptionIsaImpl] =
    ExceptionIsaFactory
}
