package arch.core.flush

import arch.configs._
import arch.core.ifu.RedirectInfo
import arch.core.exception.{ ExceptionSyncReq, ExceptionTrapUpdate }
import vutils.graph.Node
import chisel3._

class Flush(implicit p: Parameters) extends Node[Parameters]("flush") {
  val sync        = in[ExceptionSyncReq]
  val redirect    = in[RedirectInfo]
  val trapUpdate  = in[ExceptionTrapUpdate]
  val globalFlush = out[Bool]

  globalFlush.out := sync.in.valid || redirect.in.valid || trapUpdate.in.valid
}
