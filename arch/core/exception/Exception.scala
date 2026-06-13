package arch.core.exception

import arch.configs._
import arch.core.dispatch.DispatchExceptionReq
import arch.core.ifu.{ IfuExceptionReq, IfuExceptionResp }
import arch.core.rob.{ RobExceptionReq, RobExceptionResp }
import arch.core.sb.StoreBufferExceptionReq
import arch.core.scheduler.SchedulerExceptionReq
import chisel3._
import vutils.graph.Node

class Exception(implicit p: Parameters) extends Node[Parameters]("exception") {
  val redirectReq = in[ExceptionRedirectReq]
  val syncReq     = in[ExceptionSyncReq]
  val asyncReq    = in[ExceptionAsyncReq]

  val ifuReq  = out[IfuExceptionReq]
  val ifuResp = in[IfuExceptionResp]

  val dispatchReq    = out[DispatchExceptionReq]
  val storeBufferReq = out[StoreBufferExceptionReq]
  val schedulerReq   = out[SchedulerExceptionReq]

  val csrReq    = out[ExceptionCsrReq]
  val csrStatus = in[ExceptionCsrStatus]

  val robReq  = out[RobExceptionReq]
  val robResp = in[RobExceptionResp]

  val debug = out[ExceptionDebugInfo]

  private val archPc = Mux(robResp.in.empty, ifuResp.in.fetch_pc, robResp.in.commit_pc)

  private val syncAllowed  = syncReq.in.valid && !(syncReq.in.requires_csr_idle && csrStatus.in.busy)
  private val asyncAllowed =
    asyncReq.in.valid && !(asyncReq.in.requires_csr_idle && csrStatus.in.busy)

  private val takeRedirect = redirectReq.in.valid
  private val takeSync     = !takeRedirect && syncAllowed
  private val takeAsync    = !takeRedirect && !takeSync && asyncAllowed
  private val flush        = takeRedirect || takeSync || takeAsync

  private val redirectTarget =
    Mux(takeRedirect, redirectReq.in.target, Mux(takeSync, syncReq.in.target, asyncReq.in.target))
  private val trapCause      = Mux(takeSync, syncReq.in.cause, asyncReq.in.cause)
  private val trapPc         = Mux(takeSync, syncReq.in.pc, archPc)
  private val trapWriteCsr   =
    (takeSync && syncReq.in.write_csr) || (takeAsync && asyncReq.in.write_csr)
  private val trapIsRet      = takeSync && syncReq.in.trap_ret

  ifuReq.out.redirect := flush
  ifuReq.out.target   := redirectTarget

  dispatchReq.out.flush    := flush
  storeBufferReq.out.flush := flush
  schedulerReq.out.flush   := flush
  csrReq.out.flush         := flush
  robReq.out.flush         := flush

  csrReq.out.arch_pc            := archPc
  csrReq.out.trap_update.valid  := trapWriteCsr
  csrReq.out.trap_update.is_ret := trapIsRet
  csrReq.out.trap_update.pc     := trapPc
  csrReq.out.trap_update.cause  := trapCause

  debug.out.redirect_valid  := flush
  debug.out.redirect_target := redirectTarget
  debug.out.sync_valid      := takeSync
  debug.out.async_valid     := takeAsync
  debug.out.arch_pc         := archPc
  debug.out.cause           := Mux(takeSync || takeAsync, trapCause, 0.U)
}
