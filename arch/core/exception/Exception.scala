package arch.core.exception

import arch.configs._
import arch.core.ifu.RedirectInfo
import vutils.graph.{ Node, NodeConfig, NodeSelector }
import chisel3._
import chisel3.util.PriorityEncoder

class Exception(implicit p: Parameters) extends Node[Parameters]("exception") {
  override protected def cfg: NodeConfig = NodeConfig(
    selector = NodeSelector(
      ExceptionDims.ISA -> p(ISA).name
    )
  )

  val committedSync     = inVec[ExceptionSyncReq](p => p(CommitWidth))
  val committedRedirect = inVec[RedirectInfo](p => p(CommitWidth))
  val async             = in[ExceptionAsyncReq]
  val csrStatus         = in[ExceptionCsrStatus]

  val flush    = out[ExceptionFlushReq]
  val redirect = out[RedirectInfo]

  private val isaImpl = ExceptionIsaFactory.select(cfg)

  private val syncValidVec = VecInit(
    (0 until p(CommitWidth)).map(w => committedSync.in.lanes(w).valid)
  )
  private val syncAny      = syncValidVec.asUInt.orR
  private val syncSlot     = PriorityEncoder(syncValidVec.asUInt)
  private val syncReq      = WireDefault(0.U.asTypeOf(new ExceptionSyncReq))

  syncReq.valid  := syncAny
  syncReq.kind   := committedSync.in.lanes(syncSlot).kind
  syncReq.target := committedSync.in.lanes(syncSlot).target
  syncReq.pc     := committedSync.in.lanes(syncSlot).pc

  private val redirectValidVec = VecInit(
    (0 until p(CommitWidth)).map(w => committedRedirect.in.lanes(w).valid)
  )
  private val redirectAny      = redirectValidVec.asUInt.orR
  private val redirectSlot     = PriorityEncoder(redirectValidVec.asUInt)
  private val redirectReq      = WireDefault(0.U.asTypeOf(new RedirectInfo))

  redirectReq.valid  := !syncAny && redirectAny
  redirectReq.target := committedRedirect.in.lanes(redirectSlot).target

  private val syncFlush     = isaImpl.handleSync(syncReq, csrStatus.in.busy)
  private val redirectFlush = isaImpl.handleRedirect(redirectReq)
  private val asyncFlush    = isaImpl.handleAsync(async.in, syncReq.pc, csrStatus.in.busy)

  flush.out := Mux(
    syncFlush.valid,
    syncFlush,
    Mux(
      redirectFlush.valid,
      redirectFlush,
      asyncFlush
    )
  )

  redirect.out.valid  := flush.out.valid
  redirect.out.target := flush.out.target
}
