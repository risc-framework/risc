package arch.core.exception

import arch.configs._
import chisel3._
import chisel3.util.BitPat

trait ExceptionSyncEntry {
  def kind: BitPat
  def cause: BigInt
  def priority: Int
  def writeCsr: Boolean
  def isRet: Boolean
  def requiresCsrIdle: Boolean

  def matches(req: ExceptionSyncReq, kindWidth: Int): Bool =
    req.valid && req.kind === kind.value.U(kindWidth.W)

  def handle(req: ExceptionSyncReq, csrBusy: Bool, kindWidth: Int, causeWidth: Int)(implicit
    p: Parameters
  ): ExceptionFlushReq = {
    val out        = WireDefault(0.U.asTypeOf(new ExceptionFlushReq))
    val allowed    = matches(req, kindWidth) && !(requiresCsrIdle.B && csrBusy)
    val causeValue = cause.U(causeWidth.W)

    out.valid  := allowed
    out.target := req.target
    out.source := ExceptionSource.SYNC
    out.kind   := req.kind
    out.cause  := causeValue

    out.trap_update.valid  := allowed && writeCsr.B
    out.trap_update.is_ret := isRet.B
    out.trap_update.pc     := req.pc
    out.trap_update.kind   := req.kind
    out.trap_update.cause  := causeValue

    out
  }
}
