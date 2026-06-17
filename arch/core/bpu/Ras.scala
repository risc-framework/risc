package arch.core.bpu

import arch.configs._
import chisel3._
import chisel3.util.{ isPow2, log2Ceil }
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
  private val commitSp    = RegInit(0.U(ptrW.W))
  private val specSp      = RegInit(0.U(ptrW.W))
  private val commitCount = RegInit(0.U(log2Ceil(p(RasSize) + 1).W))
  private val specCount   = RegInit(0.U(log2Ceil(p(RasSize) + 1).W))

  private def inc(ptr: UInt): UInt =
    Mux(ptr === (p(RasSize) - 1).U, 0.U, ptr + 1.U)(ptrW - 1, 0)

  private def dec(ptr: UInt): UInt =
    Mux(ptr === 0.U, (p(RasSize) - 1).U, ptr - 1.U)(ptrW - 1, 0)

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
      val popSp    = Mux(count =/= 0.U, dec(sp), sp)
      val popCount = Mux(count =/= 0.U, count - 1.U, count)

      stackNext(popSp) := pushAddr
      nextSp     := inc(popSp)
      nextCount  := Mux(popCount === p(RasSize).U, popCount, popCount + 1.U)
    }.elsewhen(kind === BpuBranchKind.RET) {
      when(count =/= 0.U) {
        nextSp    := dec(sp)
        nextCount := count - 1.U
      }
    }

    (nextSp, nextCount)
  }

  private val topPtr = dec(specSp)

  resp.out.valid  := specCount =/= 0.U
  resp.out.target := Mux(resp.out.valid, specStack(topPtr), 0.U)

  private val commitPushAddr = req.in.update.pc + p(PCStep).U(p(XLen).W)
  private val doCommitOp     = req.in.update.valid && req.in.update.taken
  private val doSpecOp       = req.in.accept &&
    (req.in.predictKind === BpuBranchKind.CALL ||
      req.in.predictKind === BpuBranchKind.RET ||
      req.in.predictKind === BpuBranchKind.CALL_RET)

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

  private val restoreSpec = req.in.flush || (req.in.update.valid && req.in.update.mispredict)
  private val specBaseStack = WireDefault(specStack)
  private val specBaseSp    = WireDefault(specSp)
  private val specBaseCount = WireDefault(specCount)

  when(restoreSpec) {
    specBaseStack := commitStackNext
    specBaseSp    := commitNextSp
    specBaseCount := commitNextCount
  }

  private val specStackNext = WireDefault(specBaseStack)
  private val specNextSp    = WireDefault(specBaseSp)
  private val specNextCount = WireDefault(specBaseCount)

  when(doSpecOp) {
    val next = applyOp(specStackNext, specBaseSp, specBaseCount, req.in.predictKind, req.in.pushAddr)

    specNextSp    := next._1
    specNextCount := next._2
  }

  commitStack := commitStackNext
  commitSp    := commitNextSp
  commitCount := commitNextCount

  specStack := specStackNext
  specSp    := specNextSp
  specCount := specNextCount
}
