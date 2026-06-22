package arch.core.exception

import arch.configs._
import arch.core.ifu.IfuExceptionResp
import arch.core.rob.RobExceptionResp
import vutils.graph.{ Node, NodeConfig, NodeSelector }
import chisel3._

class Exception(implicit p: Parameters) extends Node[Parameters]("exception") {
  override protected def cfg: NodeConfig = NodeConfig(
    selector = NodeSelector(
      ExceptionDims.ISA -> p(ISA).name
    )
  )

  val redirect  = in[ExceptionRedirectReq]
  val sync      = in[ExceptionSyncReq]
  val async     = in[ExceptionAsyncReq]
  val csrStatus = in[ExceptionCsrStatus]

  val ifuResp = in[IfuExceptionResp]
  val robResp = in[RobExceptionResp]

  val flush = out[ExceptionFlushReq]
  val debug = out[ExceptionDebugInfo]

  private val isaImpl = ExceptionIsaFactory.select(cfg)
  private val archPc  = Mux(robResp.in.empty, ifuResp.in.fetch_pc, robResp.in.commit_pc)

  private val selected = isaImpl.select(
    redirect = redirect.in,
    sync = sync.in,
    async = async.in,
    csrBusy = csrStatus.in.busy,
    archPc = archPc
  )

  flush.out := selected

  debug.out.flush_valid  := selected.valid
  debug.out.flush_target := selected.target
  debug.out.source       := selected.source
  debug.out.kind         := selected.kind
  debug.out.cause        := selected.cause
  debug.out.arch_pc      := selected.arch_pc
  debug.out.redirect     := selected.source === ExceptionSource.REDIRECT
  debug.out.sync         := selected.source === ExceptionSource.SYNC
  debug.out.async        := selected.source === ExceptionSource.ASYNC
}
