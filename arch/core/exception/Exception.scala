package arch.core.exception

import arch.core.csr.CsrTrapUpdate
import arch.core.interrupt.TrapCandidate
import arch.core.dispatch.DispatchExceptionIO
import arch.configs._
import vutils.graph.{ Node, NodeConfig, NodeSelector, NodeType }
import chisel3._

class ExceptionIO(implicit p: Parameters) extends Bundle {
  val flush         = new ExceptionFlushIO
  val ifu           = new ExceptionIfuIO
  val sb            = new ExceptionStoreBufferIO
  val scheduler     = new ExceptionSchedulerIO
  val fu_pool       = new ExceptionFuPoolIO
  val rob           = new ExceptionRobIO
  val dispatch      = Flipped(new DispatchExceptionIO)
  val interrupt     = Input(new TrapCandidate)
  val csrBusy       = Input(Bool())
  val archPc        = Input(UInt(p(XLen).W))
  val redirect      = Output(new RedirectBundle)
  val csrTrapUpdate = Output(new CsrTrapUpdate)
}

class Exception(implicit p: Parameters) extends Node(new ExceptionIO) {
  private val cfg = NodeConfig(
    selector = NodeSelector(
      ExceptionDims.ISA -> p(ISA).name
    )
  )

  override def nodeType: NodeType  = ExceptionMeta.Type
  override def desiredName: String = s"exception_${cfg.selector.canonicalName}"

  private val isaImpl  = ExceptionIsaFactory.select(cfg)
  private val selected = isaImpl.select(io.interrupt, io.flush, io.csrBusy, io.archPc)

  io.redirect      := selected._1
  io.csrTrapUpdate := selected._2

  io.ifu.redirect := selected._1.valid
  io.ifu.target   := selected._1.target

  io.sb.flush := selected._1.valid

  io.scheduler.flush := selected._1.valid

  io.fu_pool.flush := selected._1.valid

  io.rob.flush := selected._1.valid

  io.dispatch.flush := selected._1.valid
}
