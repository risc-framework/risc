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

  private def chooseRedirect(
    lhs: RedirectInfo,
    lhsPriority: UInt,
    rhs: RedirectInfo,
    rhsPriority: UInt
  )(implicit p: Parameters): (RedirectInfo, UInt) = {
    val out      = Wire(new RedirectInfo)
    val priority = Wire(UInt(8.W))
    val takeRhs  = rhs.valid && (!lhs.valid || rhsPriority < lhsPriority)

    out      := Mux(takeRhs, rhs, lhs)
    priority := Mux(takeRhs, rhsPriority, lhsPriority)

    (out, priority)
  }

  private def chooseSync(
    lhsSync: ExceptionSyncReq,
    lhsTrap: ExceptionTrapUpdate,
    lhsPriority: UInt,
    rhsSync: ExceptionSyncReq,
    rhsTrap: ExceptionTrapUpdate,
    rhsPriority: UInt
  )(implicit p: Parameters): (ExceptionSyncReq, ExceptionTrapUpdate, UInt) = {
    val sync     = Wire(new ExceptionSyncReq)
    val trap     = Wire(new ExceptionTrapUpdate)
    val priority = Wire(UInt(8.W))
    val takeRhs  = rhsSync.valid && (!lhsSync.valid || rhsPriority < lhsPriority)

    sync     := Mux(takeRhs, rhsSync, lhsSync)
    trap     := Mux(takeRhs, rhsTrap, lhsTrap)
    priority := Mux(takeRhs, rhsPriority, lhsPriority)

    (sync, trap, priority)
  }

  def handleRedirect(req: RedirectInfo)(implicit p: Parameters): RedirectInfo = {
    val invalid = WireDefault(0.U.asTypeOf(new RedirectInfo))
    val init    = (invalid, 255.U(8.W))

    redirectEntries
      .foldLeft(init) { case ((best, bestPriority), entry) =>
        val candidate = entry.handle(req)
        chooseRedirect(best, bestPriority, candidate, entry.priority.U(8.W))
      }
      ._1
  }

  def handleSync(req: ExceptionSyncReq, csrBusy: Bool)(implicit
    p: Parameters
  ): (ExceptionSyncReq, ExceptionTrapUpdate) = {
    val invalidSync = WireDefault(0.U.asTypeOf(new ExceptionSyncReq))
    val invalidTrap = WireDefault(0.U.asTypeOf(new ExceptionTrapUpdate))
    val init        = (invalidSync, invalidTrap, 255.U(8.W))

    val selected = syncEntries.foldLeft(init) { case ((bestSync, bestTrap, bestPriority), entry) =>
      val (candidateSync, candidateTrap) = entry.handle(req, csrBusy, kindWidth, causeWidth)
      chooseSync(
        bestSync,
        bestTrap,
        bestPriority,
        candidateSync,
        candidateTrap,
        entry.priority.U(8.W)
      )
    }

    (selected._1, selected._2)
  }

  def handleAsync(req: ExceptionAsyncReq, archPc: UInt, csrBusy: Bool)(implicit
    p: Parameters
  ): (ExceptionSyncReq, ExceptionTrapUpdate) = {
    val invalidSync = WireDefault(0.U.asTypeOf(new ExceptionSyncReq))
    val invalidTrap = WireDefault(0.U.asTypeOf(new ExceptionTrapUpdate))
    val init        = (invalidSync, invalidTrap, 255.U(8.W))

    val selected = asyncEntries.foldLeft(init) { case ((bestSync, bestTrap, bestPriority), entry) =>
      val (candidateSync, candidateTrap) = entry.handle(req, archPc, csrBusy, kindWidth, causeWidth)
      chooseSync(
        bestSync,
        bestTrap,
        bestPriority,
        candidateSync,
        candidateTrap,
        entry.priority.U(8.W)
      )
    }

    (selected._1, selected._2)
  }
}

object ExceptionIsaFactory extends ExceptionDims.ISA.Registry[ExceptionIsaImpl]

object ExceptionInit {
  val rv32i  = impls.isa.rv32i.ExceptionRv32iIsa.registered
  val rv32im = impls.isa.rv32im.ExceptionRv32imIsa.registered
}
