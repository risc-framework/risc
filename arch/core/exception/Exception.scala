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

  val async     = in[ExceptionAsyncReq]
  val csrStatus = in[ExceptionCsrStatus]

  val flush    = out[ExceptionFlushReq]
  val redirect = out[RedirectInfo]

  private val isaImpl = ExceptionIsaFactory.select(cfg)

  // Sync logic
  private val syncValidVec = VecInit(
    (0 until p(CommitWidth)).map(w => committedSync.in.lanes(w).valid)
  )
  private val syncAny      = syncValidVec.asUInt.orR
  private val syncSlot     = PriorityEncoder(syncValidVec.asUInt)

  private val syncRaw = WireDefault(0.U.asTypeOf(new ExceptionSyncReq))
  syncRaw.valid  := syncAny
  syncRaw.kind   := committedSync.in.lanes(syncSlot).kind
  syncRaw.target := committedSync.in.lanes(syncSlot).target
  syncRaw.pc     := committedSync.in.lanes(syncSlot).pc

  // Redirect logic
  private val redirectValidVec = VecInit(
    (0 until p(CommitWidth)).map(w => committedRedirect.in.lanes(w).valid)
  )
  private val redirectAny      = redirectValidVec.asUInt.orR
  private val redirectSlot     = PriorityEncoder(redirectValidVec.asUInt)

  private val redirectRaw = WireDefault(0.U.asTypeOf(new RedirectInfo))
  redirectRaw.valid  := !syncAny && redirectAny
  redirectRaw.target := committedRedirect.in.lanes(redirectSlot).target

  // Finalized selections
  private val selected = isaImpl.select(
    redirect = redirectRaw,
    sync = syncRaw,
    async = async.in,
    csrBusy = csrStatus.in.busy
  )

  flush.out := selected

  redirect.out.valid  := flush.out.valid
  redirect.out.target := flush.out.target
}
