package arch.core.bpu

import arch.configs._
import chisel3._
import chisel3.util.{ UIntToOH, isPow2, log2Ceil }
import vutils.graph.Node

class RasReq(implicit p: Parameters) extends Bundle {
  val accept      = Bool()
  val flush       = Bool()
  val update      = new BpuUpdate
  val predictKind = UInt(BpuBranchKind.width.W)
  val pushAddr    = UInt(p(XLen).W)
}

class RasResp(implicit p: Parameters) extends Bundle {
  val target = UInt(p(XLen).W)
  val valid  = Bool()
}

class Ras(implicit p: Parameters) extends Node[Parameters]("ras") {
  val req  = in[RasReq]
  val resp = out[RasResp]

  require(isPow2(p(RasSize)), "RasSize must be a power of 2")

  private val ptrW = log2Ceil(p(RasSize)).max(1)

  private val commitStack = RegInit(VecInit(Seq.fill(p(RasSize))(0.U(p(XLen).W))))
  private val specStack   = RegInit(VecInit(Seq.fill(p(RasSize))(0.U(p(XLen).W))))
  // An unmarked entry is read directly from the committed stack. Recovery can
  // then discard speculative contents by clearing this bitmap, without copying
  // the complete committed stack into specStack on the recovery path.
  private val specDirty   = RegInit(0.U(p(RasSize).W))
  private val commitSp    = RegInit(0.U(ptrW.W))
  private val specSp      = RegInit(0.U(ptrW.W))
  private val commitCount = RegInit(0.U(log2Ceil(p(RasSize) + 1).W))
  private val specCount   = RegInit(0.U(log2Ceil(p(RasSize) + 1).W))

  private def inc(ptr: UInt): UInt = {
    if (p(RasSize) == 1) 0.U(ptrW.W)
    else (ptr + 1.U)(ptrW - 1, 0)
  }

  private def dec(ptr: UInt): UInt = {
    if (p(RasSize) == 1) 0.U(ptrW.W)
    else (ptr - 1.U)(ptrW - 1, 0)
  }

  private def changesRas(kind: UInt): Bool =
    kind === BpuBranchKind.CALL ||
      kind === BpuBranchKind.RET ||
      kind === BpuBranchKind.CALL_RET

  private def applyOp(
    stackNext: Vec[UInt],
    sp: UInt,
    count: UInt,
    kind: UInt,
    pushAddr: UInt
  ): (UInt, UInt) = {
    val nextSp    = WireDefault(sp)
    val nextCount = WireDefault(count)

    when(kind === BpuBranchKind.CALL) {
      stackNext(sp) := pushAddr
      nextSp     := inc(sp)
      nextCount  := Mux(count === p(RasSize).U, count, count + 1.U)
    }.elsewhen(kind === BpuBranchKind.CALL_RET) {
      when(count === 0.U) {
        stackNext(sp) := pushAddr
        nextSp        := inc(sp)
        nextCount     := 1.U
      }.otherwise {
        // Replacing the popped top immediately pushes back to the same depth.
        stackNext(dec(sp)) := pushAddr
      }
    }.elsewhen(kind === BpuBranchKind.RET) {
      when(count =/= 0.U) {
        nextSp    := dec(sp)
        nextCount := count - 1.U
      }
    }

    (nextSp, nextCount)
  }

  private val commitPushAddr = req.in.update.pc + p(PCStep).U(p(XLen).W)
  private val doCommitOp = req.in.update.valid && req.in.update.taken &&
    changesRas(req.in.update.branch_kind)
  private val rawSpecOp = req.in.accept && changesRas(req.in.predictKind)

  private val commitStackNext = WireDefault(commitStack)
  private val commitNextSp    = WireDefault(commitSp)
  private val commitNextCount = WireDefault(commitCount)

  when(doCommitOp) {
    val next = applyOp(
      commitStackNext,
      commitSp,
      commitCount,
      req.in.update.branch_kind,
      commitPushAddr
    )

    commitNextSp    := next._1
    commitNextCount := next._2
  }

  // Restore speculative state one cycle after any redirect that discards the
  // predicted stream.  Keeping the restore pulse registered prevents the wide
  // ROB/BPU update selector from directly driving every RAS state control.
  // Commit training itself remains on the original cycle; by the time this
  // pulse is observed, commitSp/commitCount already include that update.
  private val restorePending = RegNext(
    req.in.flush ||
      (req.in.update.valid && req.in.update.mispredict && !req.in.update.preserve_spec),
    false.B
  )
  private val restoreSpec   = restorePending
  private val specBaseSp    = Mux(restoreSpec, commitSp, specSp)
  private val specBaseCount = Mux(restoreSpec, commitCount, specCount)

  // During the recovery cycle, prediction observes the committed stack
  // directly.  A newly accepted CALL/RET is then applied on top of that state,
  // so delaying recovery does not discard the redirected stream's first RAS op.
  private val topPtr = dec(specBaseSp)
  resp.out.valid := specBaseCount =/= 0.U
  private val topTarget = Mux(
    restoreSpec,
    commitStack(topPtr),
    Mux(specDirty(topPtr), specStack(topPtr), commitStack(topPtr))
  )
  resp.out.target := Mux(resp.out.valid, topTarget, 0.U)

  private val doSpecOp = rawSpecOp

  private val specStackNext = WireDefault(specStack)
  private val specNextSp    = WireDefault(specBaseSp)
  private val specNextCount = WireDefault(specBaseCount)

  when(doSpecOp) {
    val next = applyOp(
      specStackNext,
      specBaseSp,
      specBaseCount,
      req.in.predictKind,
      req.in.pushAddr
    )

    specNextSp    := next._1
    specNextCount := next._2
  }

  private val specWritesEntry = doSpecOp &&
    (req.in.predictKind === BpuBranchKind.CALL || req.in.predictKind === BpuBranchKind.CALL_RET)
  private val specWriteIndex = Mux(
    req.in.predictKind === BpuBranchKind.CALL_RET && specBaseCount =/= 0.U,
    dec(specBaseSp),
    specBaseSp
  )
  private val specDirtyBase = Mux(restoreSpec, 0.U(p(RasSize).W), specDirty)
  private val specDirtyNext = Mux(
    specWritesEntry,
    specDirtyBase | UIntToOH(specWriteIndex, p(RasSize)),
    specDirtyBase
  )

  commitStack := commitStackNext
  commitSp    := commitNextSp
  commitCount := commitNextCount

  specStack := specStackNext
  specDirty := specDirtyNext
  specSp    := specNextSp
  specCount := specNextCount
}
