package arch.core.exception

import arch.core.flush.FlushExceptionIO
import arch.core.fupool.FuPoolExceptionIO
import arch.core.ifu.IfuExceptionIO
import arch.core.interrupt.InterruptExceptionIO
import arch.core.rob.RobExceptionIO
import arch.configs._
import vutils.graph.{ NodeDim, NodeDimensionImpl, NodeDimensionRegistry, NodePort, NodeType }
import chisel3._

object ExceptionMeta {
  val Type      = NodeType("exception")
  val FLUSH     = NodePort[ExceptionIO, FlushExceptionIO]("flush", _.flush)
  val IFU       = NodePort[ExceptionIO, IfuExceptionIO]("ifu", _.ifu)
  val DISPATCH  = NodePort[ExceptionIO, ExceptionDispatchIO]("dispatch", _.dispatch)
  val SB        = NodePort[ExceptionIO, ExceptionStoreBufferIO]("sb", _.sb)
  val SCHEDULER = NodePort[ExceptionIO, ExceptionSchedulerIO]("scheduler", _.scheduler)
  val FU_POOL   = NodePort[ExceptionIO, FuPoolExceptionIO]("fu_pool", _.fu_pool)
  val ROB       = NodePort[ExceptionIO, RobExceptionIO]("rob", _.rob)
  val INTERRUPT = NodePort[ExceptionIO, InterruptExceptionIO]("interrupt", _.interrupt)
  val DEBUG     = NodePort[ExceptionIO, ExceptionDebugIO]("debug", _.debug)
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
  )(implicit p: Parameters): (RedirectBundle, arch.core.csr.CsrTrapUpdate)
}

object ExceptionIsaFactory
    extends NodeDimensionRegistry[ExceptionIsaImpl](ExceptionMeta.Type, ExceptionDims.ISA)

object ExceptionInit {
  val rv32i  = impls.isa.rv32i.ExceptionRv32iIsa
  val rv32im = impls.isa.rv32im.ExceptionRv32imIsa
}
