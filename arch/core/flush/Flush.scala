package arch.core.flush

import arch.configs._
import arch.core.exception.{ ExceptionRedirectReq, ExceptionSyncReq }
import vutils.graph.Node
import chisel3._
import chisel3.util.PriorityEncoder

class Flush(implicit p: Parameters) extends Node[Parameters]("flush") {
  val rob      = in[FlushRobReq]
  val redirect = out[ExceptionRedirectReq]
  val sync     = out[ExceptionSyncReq]

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
  sync.out.trap_ret          := rob.in.sync(syncSlot).trap_ret
  sync.out.target            := rob.in.sync(syncSlot).target
  sync.out.pc                := rob.in.sync(syncSlot).pc
  sync.out.cause             := rob.in.sync(syncSlot).cause
  sync.out.write_csr         := rob.in.sync(syncSlot).write_csr
  sync.out.requires_csr_idle := rob.in.sync(syncSlot).requires_csr_idle

  redirect.out        := 0.U.asTypeOf(new ExceptionRedirectReq)
  redirect.out.valid  := !syncAny && redirectAny
  redirect.out.target := rob.in.redirect_target(redirectSlot)
}
