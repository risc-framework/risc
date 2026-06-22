package arch.core.exception

import arch.configs._
import arch.core.ifu.IfuExceptionResp
import arch.core.rob.RobExceptionResp
import vutils.graph.{ Node, NodeConfig, NodeSelector }
import chisel3._
import chisel3.util.PriorityEncoder

class Exception(implicit p: Parameters) extends Node[Parameters]("exception") {
  override protected def cfg: NodeConfig = NodeConfig(
    selector = NodeSelector(
      ExceptionDims.ISA -> p(ISA).name
    )
  )

  val committedSync = inVec[ExceptionSyncReq](p => p(CommitWidth))
  val sync          = out[ExceptionSyncReq]

  val committedRedirect = inVec[ExceptionRedirectReq](p => p(CommitWidth))
  val redirect          = out[ExceptionRedirectReq]

  val async     = in[ExceptionAsyncReq]
  val csrStatus = in[ExceptionCsrStatus]

  val ifuResp = in[IfuExceptionResp]
  val robResp = in[RobExceptionResp]

  val flush = out[ExceptionFlushReq]
  val debug = out[ExceptionDebugInfo]

  private val isaImpl = ExceptionIsaFactory.select(cfg)
  private val archPc  = Mux(robResp.in.empty, ifuResp.in.fetch_pc, robResp.in.commit_pc)

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

  private val selected = isaImpl.select(
    redirect = redirect.out,
    sync = sync.out,
    async = async.in,
    csrBusy = csrStatus.in.busy,
    archPc = archPc
  )

  flush.out := selected

  debug.out.flush_valid  := selected.valid
  debug.out.flush_target := selected.target
  debug.out.source       := selected.source
  debug.out.kind         := selected.kind
  debug.out.cause        := selected.cause
  debug.out.arch_pc      := selected.arch_pc
  debug.out.redirect     := selected.source === ExceptionSource.REDIRECT
  debug.out.sync         := selected.source === ExceptionSource.SYNC
  debug.out.async        := selected.source === ExceptionSource.ASYNC
}
