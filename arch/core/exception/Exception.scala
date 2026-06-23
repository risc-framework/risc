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

  val sync       = out[ExceptionSyncReq]
  val redirect   = out[RedirectInfo]
  val trapUpdate = out[ExceptionTrapUpdate]

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

  private val (syncHandled, syncTrapUpdate)   = isaImpl.handleSync(syncReq, csrStatus.in.busy)
  private val redirectHandled                 = isaImpl.handleRedirect(redirectReq)
  private val (asyncHandled, asyncTrapUpdate) =
    isaImpl.handleAsync(async.in, syncReq.pc, csrStatus.in.busy)

  private val selectedSync       = WireDefault(0.U.asTypeOf(new ExceptionSyncReq))
  private val selectedTrapUpdate = WireDefault(0.U.asTypeOf(new ExceptionTrapUpdate))

  when(syncHandled.valid) {
    selectedSync       := syncHandled
    selectedTrapUpdate := syncTrapUpdate
  }.elsewhen(!redirectHandled.valid && asyncHandled.valid) {
    selectedSync       := asyncHandled
    selectedTrapUpdate := asyncTrapUpdate
  }

  redirect.out.valid  := syncHandled.valid || redirectHandled.valid || asyncHandled.valid
  redirect.out.target := Mux(
    syncHandled.valid,
    syncHandled.target,
    Mux(redirectHandled.valid, redirectHandled.target, asyncHandled.target)
  )

  sync.out       := selectedSync
  trapUpdate.out := selectedTrapUpdate
}
