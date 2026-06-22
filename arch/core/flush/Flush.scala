package arch.core.flush

import arch.configs._
import arch.core.exception.{ ExceptionCsrReq, ExceptionFlushReq }
import arch.core.ifu.IfuExceptionReq
import vutils.graph.Node
import chisel3._

class Flush(implicit p: Parameters) extends Node[Parameters]("flush") {
  val exception   = in[ExceptionFlushReq]
  val globalFlush = out[Bool]

  val ifuReq    = out[IfuExceptionReq]
  val fuPoolReq = out[ExceptionCsrReq]

  // Global flush
  globalFlush.out := exception.in.valid

  ifuReq.out.redirect := exception.in.valid
  ifuReq.out.target   := exception.in.target

  fuPoolReq.out.flush       := exception.in.valid
  fuPoolReq.out.arch_pc     := exception.in.arch_pc
  fuPoolReq.out.trap_update := exception.in.trap_update
}
