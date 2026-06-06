package arch.core.exception

import arch.configs._
import arch.core.csr.CsrTrapUpdate
import arch.core.ifu.IfuExceptionIO
import arch.core.interrupt.TrapCandidate
import arch.core.rob.RobExceptionIO
import vutils.graph.{ NodeDim, NodeDimensionImpl, NodeDimensionRegistry, NodePort, NodeType }
import chisel3._

object ExceptionMeta {
  val Type            = NodeType("exception")
  val FLUSH           = NodePort[ExceptionIO, ExceptionFlushIO]("flush", _.flush)
  val IFU             = NodePort[ExceptionIO, IfuExceptionIO]("ifu", _.ifu)
  val DISPATCH        = NodePort[ExceptionIO, ExceptionDispatchIO]("dispatch", _.dispatch)
  val SB              = NodePort[ExceptionIO, ExceptionStoreBufferIO]("sb", _.sb)
  val SCHEDULER       = NodePort[ExceptionIO, ExceptionSchedulerIO]("scheduler", _.scheduler)
  val FU_POOL         = NodePort[ExceptionIO, ExceptionFuPoolIO]("fu_pool", _.fu_pool)
  val ROB             = NodePort[ExceptionIO, RobExceptionIO]("rob", _.rob)
  val INTERRUPT       = NodePort[ExceptionIO, TrapCandidate]("interrupt", _.interrupt)
  val CSR_BUSY        = NodePort[ExceptionIO, Bool]("csr_busy", _.csrBusy)
  val ARCH_PC         = NodePort[ExceptionIO, UInt]("arch_pc", _.archPc)
  val REDIRECT        = NodePort[ExceptionIO, RedirectBundle]("redirect", _.redirect)
  val CSR_TRAP_UPDATE = NodePort[ExceptionIO, CsrTrapUpdate]("csr_trap_update", _.csrTrapUpdate)
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
    commitRedirect: ExceptionFlushIO,
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
