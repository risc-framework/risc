package arch.core.flush

import arch.configs._
import arch.core.exception.{ ExceptionCsrReq, ExceptionFlushReq }
import vutils.graph.Node
import chisel3._

class Flush(implicit p: Parameters) extends Node[Parameters]("flush") {
  val exception   = in[ExceptionFlushReq]
  val globalFlush = out[Bool]

  val fuPoolReq = out[ExceptionCsrReq]

  // Global flush
  globalFlush.out := exception.in.valid

  fuPoolReq.out.flush       := exception.in.valid
  fuPoolReq.out.trap_update := exception.in.trap_update
}
