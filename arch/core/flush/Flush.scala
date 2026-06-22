package arch.core.flush

import arch.configs._
import arch.core.exception.{ ExceptionCsrReq, ExceptionFlushReq, ExceptionRedirectReq, ExceptionSyncReq }
import arch.core.ifu.IfuExceptionReq
import vutils.graph.Node
import chisel3._
import chisel3.util.PriorityEncoder

class Flush(implicit p: Parameters) extends Node[Parameters]("flush") {
  val committedRedirect = inVec[ExceptionRedirectReq](p => p(CommitWidth))
  val committedSync     = inVec[ExceptionSyncReq](p => p(CommitWidth))
  val exception         = in[ExceptionFlushReq]

  val globalFlush = out[Bool]
  val sync        = out[ExceptionSyncReq]
  val redirect    = out[ExceptionRedirectReq]

  val ifuReq    = out[IfuExceptionReq]
  val fuPoolReq = out[ExceptionCsrReq]

  // Global flush
  globalFlush.out := exception.in.valid

  // Sync logic
  private val syncValidVec = VecInit(
    (0 until p(CommitWidth)).map(w => committedSync.in.lanes(w).valid)
  )
  private val syncAny      = syncValidVec.asUInt.orR
  private val syncSlot     = PriorityEncoder(syncValidVec.asUInt)

  sync.out.valid  := syncAny
  sync.out.kind   := committedSync.in.lanes(syncSlot).kind
  sync.out.target := committedSync.in.lanes(syncSlot).target
  sync.out.pc     := committedSync.in.lanes(syncSlot).pc

  // Redirect logic
  private val redirectValidVec = VecInit(
    (0 until p(CommitWidth)).map(w => committedRedirect.in.lanes(w).valid)
  )
  private val redirectAny      = redirectValidVec.asUInt.orR
  private val redirectSlot     = PriorityEncoder(redirectValidVec.asUInt)

  redirect.out.valid  := !syncAny && redirectAny
  redirect.out.target := committedRedirect.in.lanes(redirectSlot).target

  ifuReq.out.redirect := exception.in.valid
  ifuReq.out.target   := exception.in.target

  fuPoolReq.out.flush       := exception.in.valid
  fuPoolReq.out.arch_pc     := exception.in.arch_pc
  fuPoolReq.out.trap_update := exception.in.trap_update
}
