package arch.core.exception

import arch.configs._
import chisel3._
import chisel3.util.BitPat

trait ExceptionAsyncEntry {
  def kind: BitPat
  def cause: BigInt
  def priority: Int
  def writeCsr: Boolean
  def requiresCsrIdle: Boolean

  def matches(req: ExceptionAsyncReq, kindWidth: Int): Bool =
    req.valid && req.kind === kind.value.U(kindWidth.W)

  def handle(req: ExceptionAsyncReq, archPc: UInt, csrBusy: Bool, kindWidth: Int, causeWidth: Int)(
    implicit p: Parameters
  ): (ExceptionSyncReq, ExceptionTrapUpdate) = {
    val sync       = WireDefault(0.U.asTypeOf(new ExceptionSyncReq))
    val trap       = WireDefault(0.U.asTypeOf(new ExceptionTrapUpdate))
    val allowed    = matches(req, kindWidth) && !(requiresCsrIdle.B && csrBusy)
    val causeValue = ((BigInt(1) << (causeWidth - 1)) | cause).U(causeWidth.W)

    sync.valid  := allowed
    sync.kind   := req.kind
    sync.target := req.target
    sync.pc     := archPc

    trap.valid  := allowed && writeCsr.B
    trap.is_ret := false.B
    trap.pc     := archPc
    trap.kind   := req.kind
    trap.cause  := causeValue

    (sync, trap)
  }
}
