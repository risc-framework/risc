package arch.core.exception

import arch.configs._
import arch.core.ifu.RedirectInfo
import chisel3._
import chisel3.util.BitPat

trait ExceptionRedirectEntry {
  def kind: BitPat
  def priority: Int

  def handle(req: RedirectInfo, kindWidth: Int, causeWidth: Int)(implicit
    p: Parameters
  ): ExceptionFlushReq = {
    val out = WireDefault(0.U.asTypeOf(new ExceptionFlushReq))

    out.valid   := req.valid
    out.target  := req.target
    out.source  := ExceptionSource.REDIRECT
    out.kind    := kind.value.U(kindWidth.W)
    out.cause   := 0.U(causeWidth.W)
    out.arch_pc := 0.U

    out
  }
}
