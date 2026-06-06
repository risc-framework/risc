package arch.core.exception

import arch.core.csr.CsrTrapUpdate
import arch.core.interrupt.TrapCandidate
import arch.configs._
import vutils.graph.{ Node, NodeConfig, NodeSelector, NodeType }
import chisel3._

class ExceptionIO(implicit p: Parameters) extends Bundle {
  val flush     = new ExceptionFlushIO
  val ifu       = new ExceptionIfuIO
  val dispatch  = new ExceptionDispatchIO
  val sb        = new ExceptionStoreBufferIO
  val scheduler = new ExceptionSchedulerIO
  val fu_pool   = new ExceptionFuPoolIO
  val rob       = new ExceptionRobIO

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
  private val selected = isaImpl.select(
    io.interrupt,
    io.flush,
    io.csrBusy,
    io.archPc
  )

  private val redirect   = selected._1
  private val trapUpdate = selected._2
  private val flush      = redirect.valid

  io.redirect      := redirect
  io.csrTrapUpdate := trapUpdate

  io.ifu.redirect := redirect.valid
  io.ifu.target   := redirect.target

  io.dispatch.flush  := flush
  io.sb.flush        := flush
  io.scheduler.flush := flush
  io.fu_pool.flush   := flush
  io.rob.flush       := flush
}
