package arch.core.exception

import arch.configs._
import arch.core.ifu.RedirectInfo
import chisel3._
import chisel3.util.BitPat

trait ExceptionRedirectEntry {
  def kind: BitPat
  def priority: Int

  def handle(req: RedirectInfo)(implicit p: Parameters): RedirectInfo = {
    val out = WireDefault(0.U.asTypeOf(new RedirectInfo))

    out.valid  := req.valid
    out.target := req.target

    out
  }
}
