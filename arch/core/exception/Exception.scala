package arch.core.exception

import arch.configs._
import arch.core.dispatch.DispatchExceptionReq
import arch.core.ifu.{ IfuExceptionReq, IfuExceptionResp }
import arch.core.rob.{ RobExceptionReq, RobExceptionResp }
import arch.core.sb.StoreBufferExceptionReq
import arch.core.scheduler.SchedulerExceptionReq
import vutils.graph.{ Node, NodeConfig, NodeSelector }
import chisel3._

class Exception(implicit p: Parameters) extends Node[Parameters]("exception") {
  override protected def cfg: NodeConfig = NodeConfig(
    selector = NodeSelector(
      ExceptionDims.ISA -> p(ISA).name
    )
  )

  val flushReq     = in[ExceptionRequest]
  val interruptReq = in[ExceptionRequest]

  val ifuReq  = out[IfuExceptionReq]
  val ifuResp = in[IfuExceptionResp]

  val dispatchReq    = out[DispatchExceptionReq]
  val storeBufferReq = out[StoreBufferExceptionReq]
  val schedulerReq   = out[SchedulerExceptionReq]

  val fuPoolReq  = out[ExceptionFuPoolReq]
  val fuPoolResp = in[ExceptionFuPoolResp]

  val robReq  = out[RobExceptionReq]
  val robResp = in[RobExceptionResp]

  val debug = out[ExceptionDebugInfo]

  private val isaImpl = ExceptionIsaFactory.select(cfg)
  private val archPc  = Mux(robResp.in.empty, ifuResp.in.fetch_pc, robResp.in.commit_pc)

  private val requests = Seq(flushReq.in, interruptReq.in)
  private val selected = isaImpl.select(
    requests,
    fuPoolResp.in.csr_busy,
    archPc
  )

  private val redirect   = selected._1
  private val trapUpdate = selected._2
  private val flush      = redirect.valid

  ifuReq.out.redirect := redirect.valid
  ifuReq.out.target   := redirect.target

  dispatchReq.out.flush    := flush
  storeBufferReq.out.flush := flush
  schedulerReq.out.flush   := flush
  fuPoolReq.out.flush      := flush
  robReq.out.flush         := flush

  fuPoolReq.out.arch_pc     := archPc
  fuPoolReq.out.trap_update := trapUpdate

  debug.out.redirect := redirect
  debug.out.arch_pc  := archPc
}
