package arch.core.flush

import arch.configs._
import arch.core.dispatch.DispatchExceptionReq
import arch.core.exception.{ ExceptionCsrReq, ExceptionFlushReq, ExceptionRedirectReq, ExceptionSyncReq }
import arch.core.ifu.IfuExceptionReq
import arch.core.rob.RobExceptionReq
import arch.core.sb.StoreBufferExceptionReq
import arch.core.scheduler.SchedulerExceptionReq
import vutils.graph.Node
import chisel3._
import chisel3.util.PriorityEncoder

class Flush(implicit p: Parameters) extends Node[Parameters]("flush") {
  val rob       = in[FlushRobReq]
  val exception = in[ExceptionFlushReq]

  val redirect = out[ExceptionRedirectReq]
  val sync     = out[ExceptionSyncReq]

  val ifuReq         = out[IfuExceptionReq]
  val dispatchReq    = out[DispatchExceptionReq]
  val storeBufferReq = out[StoreBufferExceptionReq]
  val schedulerReq   = out[SchedulerExceptionReq]
  val fuPoolReq      = out[ExceptionCsrReq]
  val robReq         = out[RobExceptionReq]

  private val syncValidVec     = VecInit((0 until p(CommitWidth)).map(w => rob.in.sync(w).valid))
  private val redirectValidVec = VecInit(
    (0 until p(CommitWidth)).map(w => rob.in.redirect_valid(w))
  )
  private val syncAny          = syncValidVec.asUInt.orR
  private val redirectAny      = redirectValidVec.asUInt.orR
  private val syncSlot         = PriorityEncoder(syncValidVec.asUInt)
  private val redirectSlot     = PriorityEncoder(redirectValidVec.asUInt)

  sync.out                   := 0.U.asTypeOf(new ExceptionSyncReq)
  sync.out.valid             := syncAny
  sync.out.kind              := rob.in.sync(syncSlot).kind
  sync.out.target            := rob.in.sync(syncSlot).target
  sync.out.pc                := rob.in.sync(syncSlot).pc
  sync.out.requires_csr_idle := rob.in.sync(syncSlot).requires_csr_idle

  redirect.out        := 0.U.asTypeOf(new ExceptionRedirectReq)
  redirect.out.valid  := !syncAny && redirectAny
  redirect.out.target := rob.in.redirect_target(redirectSlot)

  ifuReq.out.redirect := exception.in.valid
  ifuReq.out.target   := exception.in.target

  dispatchReq.out.flush    := exception.in.valid
  storeBufferReq.out.flush := exception.in.valid
  schedulerReq.out.flush   := exception.in.valid
  robReq.out.flush         := exception.in.valid

  fuPoolReq.out.flush       := exception.in.valid
  fuPoolReq.out.arch_pc     := exception.in.arch_pc
  fuPoolReq.out.trap_update := exception.in.trap_update
}
