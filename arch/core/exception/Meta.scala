package arch.core.exception

import arch.core.csr.CsrTrapUpdate
import arch.configs._
import vutils.graph.NodeDims
import chisel3._

object ExceptionDims extends NodeDims("exception") {
  val ISA = dim("isa")
}

trait ExceptionIsaImpl extends ExceptionDims.ISA.Impl {
  def select(
    requests: Seq[ExceptionRequest],
    csrBusy: Bool,
    archPc: UInt
  )(implicit p: Parameters): (RedirectBundle, CsrTrapUpdate)
}

object ExceptionIsaFactory extends ExceptionDims.ISA.Registry[ExceptionIsaImpl]

object ExceptionInit {
  val rv32i  = impls.isa.rv32i.ExceptionRv32iIsa.registered
  val rv32im = impls.isa.rv32im.ExceptionRv32imIsa.registered
}
