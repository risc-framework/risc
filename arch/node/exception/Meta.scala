package arch.node.exception

import arch.configs._
import arch.node.csr.CsrTrapUpdate
import arch.node.interrupt.TrapCandidate
import vutils.graph.{ NodeDim, NodeDimensionImpl, NodeDimensionRegistry, NodePort, NodeType }
import chisel3._

object ExceptionMeta {
  val Type           = NodeType("exception")
  val INTERRUPT      = NodePort[ExceptionIO, TrapCandidate]("interrupt", _.interrupt)
  val COMMITREDIRECT = NodePort[ExceptionIO, RedirectBundle]("commit_redirect", _.commitRedirect)
  val REDIRECT       = NodePort[ExceptionIO, RedirectBundle]("redirect", _.redirect)
  val CSRTRAPUPDATE  = NodePort[ExceptionIO, CsrTrapUpdate]("csr_trap_update", _.csrTrapUpdate)
}

object ExceptionDims {
  val ISA = NodeDim("isa")
}

trait ExceptionIsaImpl extends NodeDimensionImpl {
  override def nodeType: NodeType = ExceptionMeta.Type
  override def dim: NodeDim       = ExceptionDims.ISA
  override def name: String       = value

  def select(
    interrupt: TrapCandidate,
    commitRedirect: RedirectBundle,
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
