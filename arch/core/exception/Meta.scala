package arch.core.exception

import arch.configs._
import arch.core.ifu.RedirectInfo
import vutils.graph.NodeDims
import chisel3._
import chisel3.util.BitPat

object ExceptionDims extends NodeDims("exception") {
  val ISA = dim("isa")
}

trait ExceptionHandleEntry {
  def kind: BitPat
  def cause: BigInt
  def priority: Int
  def writeCsr: Boolean
  def isRet: Boolean
  def isInterrupt: Boolean
  def requiresCsrIdle: Boolean

  def causeValue(causeWidth: Int): BigInt =
    if (isInterrupt) (BigInt(1) << (causeWidth - 1)) | cause else cause

  def handle(req: ExceptionRawReq, ctx: ExceptionHandleContext, causeWidth: Int)(implicit
    p: Parameters
  ): ExceptionResolvedReq = {
    val out = Wire(new ExceptionResolvedReq)

    out.valid             := req.valid
    out.source            := req.source
    out.kind              := req.kind
    out.target            := req.target
    out.pc                := Mux(isInterrupt.B, ctx.arch_pc, req.pc)
    out.cause             := causeValue(causeWidth).U(causeWidth.W)
    out.priority          := priority.U
    out.write_csr         := writeCsr.B
    out.is_ret            := isRet.B
    out.requires_csr_idle := requiresCsrIdle.B

    out
  }
}

case class CommonExceptionHandleEntry(
  kind: BitPat,
  cause: BigInt,
  priority: Int,
  writeCsr: Boolean = true,
  isRet: Boolean = false,
  isInterrupt: Boolean = false,
  requiresCsrIdle: Boolean = true
) extends ExceptionHandleEntry

trait ExceptionIsaImpl extends ExceptionDims.ISA.Impl {
  def kindWidth: Int
  def causeWidth: Int
  def entries: Seq[ExceptionHandleEntry]
  def redirectKind: UInt

  def kindValue(kind: BitPat): UInt =
    kind.value.U(kindWidth.W)

  private def resolve(req: ExceptionRawReq, ctx: ExceptionHandleContext)(implicit
    p: Parameters
  ): ExceptionResolvedReq = {
    val out = Wire(new ExceptionResolvedReq)

    out := 0.U.asTypeOf(new ExceptionResolvedReq)

    for (entry <- entries)
      when(req.kind === kindValue(entry.kind)) {
        out := entry.handle(req, ctx, causeWidth)
      }

    out
  }

  private def chooseBetter(lhs: ExceptionResolvedReq, rhs: ExceptionResolvedReq)(implicit
    p: Parameters
  ): ExceptionResolvedReq = {
    val out     = Wire(new ExceptionResolvedReq)
    val takeRhs = rhs.valid && (!lhs.valid || rhs.priority < lhs.priority)

    out := lhs

    when(takeRhs) {
      out := rhs
    }

    out
  }

  def select(
    redirect: RedirectInfo,
    sync: ExceptionSyncReq,
    async: ExceptionAsyncReq,
    csrBusy: Bool,
  )(implicit p: Parameters): ExceptionFlushReq = {
    val ctx      = Wire(new ExceptionHandleContext)
    val raw      = Wire(Vec(3, new ExceptionRawReq))
    val resolved = Wire(Vec(3, new ExceptionResolvedReq))
    val gated    = Wire(Vec(3, new ExceptionResolvedReq))
    val invalid  = Wire(new ExceptionResolvedReq)
    val best0    = Wire(new ExceptionResolvedReq)
    val best1    = Wire(new ExceptionResolvedReq)
    val best2    = Wire(new ExceptionResolvedReq)
    val out      = Wire(new ExceptionFlushReq)

    ctx.arch_pc  := sync.pc
    ctx.csr_busy := csrBusy

    raw(0)        := 0.U.asTypeOf(new ExceptionRawReq)
    raw(0).valid  := redirect.valid
    raw(0).source := ExceptionSource.REDIRECT
    raw(0).kind   := redirectKind
    raw(0).target := redirect.target
    raw(0).pc     := 0.U(p(XLen).W)

    raw(1)        := 0.U.asTypeOf(new ExceptionRawReq)
    raw(1).valid  := sync.valid
    raw(1).source := ExceptionSource.SYNC
    raw(1).kind   := sync.kind
    raw(1).target := sync.target
    raw(1).pc     := sync.pc

    raw(2)        := 0.U.asTypeOf(new ExceptionRawReq)
    raw(2).valid  := async.valid
    raw(2).source := ExceptionSource.ASYNC
    raw(2).kind   := async.kind
    raw(2).target := async.target
    raw(2).pc     := 0.U(p(XLen).W)

    for (i <- 0 until 3) {
      resolved(i) := resolve(raw(i), ctx)
      gated(i)    := resolved(i)

      when(resolved(i).requires_csr_idle && csrBusy) {
        gated(i).valid := false.B
      }
    }

    invalid := 0.U.asTypeOf(new ExceptionResolvedReq)
    best0   := chooseBetter(invalid, gated(0))
    best1   := chooseBetter(best0, gated(1))
    best2   := chooseBetter(best1, gated(2))

    out := 0.U.asTypeOf(new ExceptionFlushReq)

    out.valid   := best2.valid
    out.target  := best2.target
    out.source  := best2.source
    out.kind    := best2.kind
    out.cause   := best2.cause
    out.arch_pc := best2.pc

    out.trap_update.valid  := best2.valid && best2.write_csr
    out.trap_update.is_ret := best2.is_ret
    out.trap_update.pc     := best2.pc
    out.trap_update.kind   := best2.kind
    out.trap_update.cause  := best2.cause

    out
  }
}

object ExceptionIsaFactory extends ExceptionDims.ISA.Registry[ExceptionIsaImpl]

object ExceptionInit {
  val rv32i  = impls.isa.rv32i.ExceptionRv32iIsa.registered
  val rv32im = impls.isa.rv32im.ExceptionRv32imIsa.registered
}
