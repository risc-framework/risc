package arch.core.exception

import arch.configs._
import arch.core.flush.FlushExceptionIO
import arch.core.fupool.FuPoolExceptionIO
import arch.core.ifu.IfuExceptionIO
import arch.core.interrupt.InterruptExceptionIO
import arch.core.rob.RobExceptionIO
import vutils.graph.{ Node, NodeConfig, NodeSelector, NodeType }
import chisel3._

class ExceptionIO(implicit p: Parameters) extends Bundle {
  val flush     = Flipped(new FlushExceptionIO)
  val ifu       = Flipped(new IfuExceptionIO)
  val dispatch  = new ExceptionDispatchIO
  val sb        = new ExceptionStoreBufferIO
  val scheduler = new ExceptionSchedulerIO
  val fu_pool   = Flipped(new FuPoolExceptionIO)
  val rob       = Flipped(new RobExceptionIO)
  val interrupt = Flipped(new InterruptExceptionIO)
  val debug     = new ExceptionDebugIO
}

class Exception(implicit p: Parameters) extends Node(new ExceptionIO) {
  private val cfg = NodeConfig(
    selector = NodeSelector(
      ExceptionDims.ISA -> p(ISA).name
    )
  )

  override def nodeType: NodeType  = ExceptionMeta.Type
  override def desiredName: String = s"exception_${cfg.selector.canonicalName}"

  private val isaImpl = ExceptionIsaFactory.select(cfg)
  private val archPc  = Mux(io.rob.empty, io.ifu.fetch_pc, io.rob.commit_pc)

  private val requests =
    Seq(io.flush.request, io.interrupt.request)

  private val selected = isaImpl.select(
    requests,
    io.fu_pool.csr_busy,
    archPc
  )

  private val redirect   = selected._1
  private val trapUpdate = selected._2
  private val flush      = redirect.valid

  io.ifu.redirect := redirect.valid
  io.ifu.target   := redirect.target

  io.dispatch.flush  := flush
  io.sb.flush        := flush
  io.scheduler.flush := flush
  io.fu_pool.flush   := flush
  io.rob.flush       := flush

  io.fu_pool.arch_pc     := archPc
  io.fu_pool.trap_update := trapUpdate

  io.debug.redirect := redirect
  io.debug.arch_pc  := archPc
}
