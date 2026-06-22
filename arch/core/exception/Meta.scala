package arch.core.exception

import arch.configs._
import arch.core.ifu.RedirectInfo
import vutils.graph.NodeDims
import chisel3._

object ExceptionDims extends NodeDims("exception") {
  val ISA = dim("isa")
}

trait ExceptionIsaImpl extends ExceptionDims.ISA.Impl {
  def kindWidth: Int
  def causeWidth: Int

  def redirectEntries: Seq[ExceptionRedirectEntry]
  def syncEntries: Seq[ExceptionSyncEntry]
  def asyncEntries: Seq[ExceptionAsyncEntry]

  private def chooseBetter(
    lhs: ExceptionFlushReq,
    lhsPriority: UInt,
    rhs: ExceptionFlushReq,
    rhsPriority: UInt
  )(implicit p: Parameters): (ExceptionFlushReq, UInt) = {
    val out      = Wire(new ExceptionFlushReq)
    val priority = Wire(UInt(8.W))
    val takeRhs  = rhs.valid && (!lhs.valid || rhsPriority < lhsPriority)

    out      := Mux(takeRhs, rhs, lhs)
    priority := Mux(takeRhs, rhsPriority, lhsPriority)

    (out, priority)
  }

  def handleRedirect(req: RedirectInfo)(implicit p: Parameters): ExceptionFlushReq = {
    val invalid = WireDefault(0.U.asTypeOf(new ExceptionFlushReq))
    val init    = (invalid, 255.U(8.W))

    redirectEntries
      .foldLeft(init) { case ((best, bestPriority), entry) =>
        val candidate = entry.handle(req, kindWidth, causeWidth)
        chooseBetter(best, bestPriority, candidate, entry.priority.U(8.W))
      }
      ._1
  }

  def handleSync(
    req: ExceptionSyncReq,
    csrBusy: Bool
  )(implicit p: Parameters): ExceptionFlushReq = {
    val invalid = WireDefault(0.U.asTypeOf(new ExceptionFlushReq))
    val init    = (invalid, 255.U(8.W))

    syncEntries
      .foldLeft(init) { case ((best, bestPriority), entry) =>
        val candidate = entry.handle(req, csrBusy, kindWidth, causeWidth)
        chooseBetter(best, bestPriority, candidate, entry.priority.U(8.W))
      }
      ._1
  }

  def handleAsync(req: ExceptionAsyncReq, csrBusy: Bool)(implicit
    p: Parameters
  ): ExceptionFlushReq = {
    val invalid = WireDefault(0.U.asTypeOf(new ExceptionFlushReq))
    val init    = (invalid, 255.U(8.W))

    asyncEntries
      .foldLeft(init) { case ((best, bestPriority), entry) =>
        val candidate = entry.handle(req, csrBusy, kindWidth, causeWidth)
        chooseBetter(best, bestPriority, candidate, entry.priority.U(8.W))
      }
      ._1
  }
}

object ExceptionIsaFactory extends ExceptionDims.ISA.Registry[ExceptionIsaImpl]

object ExceptionInit {
  val rv32i  = impls.isa.rv32i.ExceptionRv32iIsa.registered
  val rv32im = impls.isa.rv32im.ExceptionRv32imIsa.registered
}
