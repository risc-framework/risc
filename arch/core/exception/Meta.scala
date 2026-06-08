package arch.core.exception

import arch.core.csr.CsrTrapUpdate
import arch.configs._
import vutils.graph.{ NodeDim, NodeDimensionImpl, NodeDimensionRegistry, NodeType }
import chisel3._

object ExceptionMeta {
  val Type = NodeType("exception")
}

object ExceptionDims {
  val ISA = NodeDim("isa")
}

trait ExceptionIsaImpl extends NodeDimensionImpl {
  override def nodeType: NodeType = ExceptionMeta.Type
  override def dim: NodeDim       = ExceptionDims.ISA
  override def name: String       = value

  def select(
    requests: Seq[ExceptionRequest],
    csrBusy: Bool,
    archPc: UInt
  )(implicit p: Parameters): (RedirectBundle, CsrTrapUpdate)
}

object ExceptionIsaFactory
    extends NodeDimensionRegistry[ExceptionIsaImpl](ExceptionMeta.Type, ExceptionDims.ISA)

object ExceptionInit {
  val rv32i  = impls.isa.rv32i.ExceptionRv32iIsa
  val rv32im = impls.isa.rv32im.ExceptionRv32imIsa
}
