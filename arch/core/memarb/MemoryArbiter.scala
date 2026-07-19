package arch.core.memarb

import arch.configs._
import chisel3._
import chisel3.util.{ Arbiter, Mux1H, OHToUInt, PriorityEncoderOH, Queue, RRArbiter, UIntToOH, log2Ceil }
import vutils.graph.Node

class MemoryArbiter(implicit p: Parameters) extends Node[Parameters]("memory_arbiter") {
  val loadMemReq   = inDVec[MemoryArbiterLoadReq](p => p(NumLDs))
  val loadMemResp  = outDVec[MemoryArbiterCacheResp](p => p(NumLDs))
  val loadMmioReq  = inDVec[MemoryArbiterCacheReq](p => p(NumLDs))
  val loadMmioResp = outDVec[MemoryArbiterCacheResp](p => p(NumLDs))

  val sbMemReq   = inD[MemoryArbiterCacheReq]
  val sbMemResp  = outD[MemoryArbiterCacheResp]
  val sbMmioReq  = inD[MemoryArbiterCacheReq]
  val sbMmioResp = outD[MemoryArbiterCacheResp]

  val dcacheReq  = outD[MemoryArbiterCacheReq]
  val dcacheResp = inD[MemoryArbiterCacheResp]
  val mmioReq    = outD[MemoryArbiterCacheReq]
  val mmioResp   = inD[MemoryArbiterCacheResp]

  private val numLoadPorts = p(NumLDs)

  private val numReqs     = numLoadPorts + 1
  private val targetW     = log2Ceil(numReqs).max(1)
  private val storeTarget = numLoadPorts

  // Cacheable stores share the round-robin domain with loads so a stream of
  // load requests cannot indefinitely block the committed StoreBuffer head.
  // The selected request still enters the registered stage below.
  private val memArb =
    Module(new RRArbiter(new MemoryArbiterRoutedReq(targetW), numReqs))
  private val mmioLdArb =
    Module(new Arbiter(new MemoryArbiterRoutedReq(targetW), numLoadPorts))

  private val memRespQ  = Module(new Queue(UInt(targetW.W), p(RobSize), pipe = false, flow = false))
  private val mmioRespQ = Module(new Queue(UInt(targetW.W), p(RobSize), pipe = false, flow = false))

  private val memReqValid = RegInit(false.B)
  private val memReqBits  = Reg(new MemoryArbiterRoutedReq(targetW))

  private val mmioReqValid = RegInit(false.B)
  private val mmioReqBits  = Reg(new MemoryArbiterRoutedReq(targetW))

  for (i <- 0 until numLoadPorts) {
    memArb.io.in(i).valid       := loadMemReq.in.lanes(i).valid
    memArb.io.in(i).bits.target := i.U(targetW.W)
    memArb.io.in(i).bits.req    := loadMemReq.in.lanes(i).bits.req

    mmioLdArb.io.in(i).valid       := loadMmioReq.in.lanes(i).valid
    mmioLdArb.io.in(i).bits.target := i.U(targetW.W)
    mmioLdArb.io.in(i).bits.req    := loadMmioReq.in.lanes(i).bits
    loadMmioReq.in.lanes(i).ready  := mmioLdArb.io.in(i).ready
  }

  memArb.io.in(storeTarget).valid       := sbMemReq.in.valid
  memArb.io.in(storeTarget).bits.target := storeTarget.U(targetW.W)
  memArb.io.in(storeTarget).bits.req    := sbMemReq.in.bits

  private val memChosenValid = memArb.io.out.valid
  private val memChosenBits  = Wire(new MemoryArbiterRoutedReq(targetW))

  memChosenBits := memArb.io.out.bits
  // Read data is unused.  Do not let the load-valid arbitration result become
  // a reset/select input on all 32 registered request-data bits.
  memChosenBits.req.data := sbMemReq.in.bits.data

  // The bypass selector is physically independent from the normal request
  // valid/payload cone.  Its inputs are sourced only from Ld state and address
  // registers, so Scheduler-accept logic cannot reach D-cache.  Static lane
  // priority is sufficient because a selected Ld cannot present another
  // bypass request until its current response returns.
  private val memBypassValidVec =
    VecInit((0 until numLoadPorts).map(i => loadMemReq.in.lanes(i).bits.bypass_valid))
  private val memBypassSelectOH =
    PriorityEncoderOH(memBypassValidVec.asUInt)
  private val memBypassReqVec =
    VecInit((0 until numLoadPorts).map(i => loadMemReq.in.lanes(i).bits.bypass_req))
  private val memBypassReq =
    Mux1H(memBypassSelectOH, memBypassReqVec)
  private val memBypassTarget = Wire(UInt(targetW.W))
  memBypassTarget := OHToUInt(memBypassSelectOH)

  // A waiting committed store disables bypass so a continuous load stream
  // cannot starve the normal round-robin domain.
  private val memCanBypass =
    !memReqValid && !sbMemReq.in.valid && memBypassValidVec.asUInt.orR
  private val memOutgoingValid = memReqValid || memCanBypass
  private val memOutgoingReq   = Mux(
    memReqValid,
    memReqBits.req,
    memBypassReq
  )
  private val memOutgoingTarget = Mux(
    memReqValid,
    memReqBits.target,
    memBypassTarget
  )

  dcacheReq.out.valid := memOutgoingValid && memRespQ.io.enq.ready
  dcacheReq.out.bits  := memOutgoingReq

  private val memIssueFire  = dcacheReq.out.fire
  private val memStageReady = !memReqValid || memIssueFire

  // While a direct request is active, leave the normal stage empty and accept
  // exactly the selected bypass input only when D-cache accepts it.
  memArb.io.out.ready := memStageReady && !memCanBypass
  sbMemReq.in.ready   := memArb.io.in(storeTarget).ready

  for (i <- 0 until numLoadPorts) {
    val bypassGrant =
      memCanBypass && memIssueFire && memBypassSelectOH(i)

    loadMemReq.in.lanes(i).ready := memArb.io.in(i).ready || bypassGrant
  }

  memRespQ.io.enq.valid := memIssueFire
  memRespQ.io.enq.bits  := memOutgoingTarget

  // Keep the stage payload enable independent of upstream request validity.
  // Otherwise synthesis can fold a long upstream request-valid path into the
  // reset/enable pins of every payload register.  Payload contents are don't
  // care while valid is low, so loading them whenever the stage advances is
  // equivalent while leaving validity on the narrow control register only.
  when(memStageReady) {
    memReqValid := memChosenValid && !memCanBypass
    when(memChosenValid && !memCanBypass) {
      memReqBits := memChosenBits
    }
  }

  private val memTarget       = memRespQ.io.deq.bits
  private val memRespValid    = dcacheResp.in.valid && memRespQ.io.deq.valid
  private val memRespReadyVec = Wire(Vec(numReqs, Bool()))

  for (i <- 0 until numLoadPorts) {
    loadMemResp.out.lanes(i).valid := memRespValid && memTarget === i.U
    loadMemResp.out.lanes(i).bits  := dcacheResp.in.bits
    memRespReadyVec(i)             := loadMemResp.out.lanes(i).ready
  }

  sbMemResp.out.valid          := memRespValid && memTarget === storeTarget.U
  sbMemResp.out.bits           := dcacheResp.in.bits
  memRespReadyVec(storeTarget) := sbMemResp.out.ready

  private val memTargetReady = (memRespReadyVec.asUInt & UIntToOH(memTarget, numReqs)).orR

  dcacheResp.in.ready   := memRespQ.io.deq.valid && memTargetReady
  memRespQ.io.deq.ready := dcacheResp.in.valid && memTargetReady

  private val mmioLdSelected    = mmioLdArb.io.out.valid
  private val mmioStoreSelected = !mmioLdSelected && sbMmioReq.in.valid
  private val mmioChosenValid   = mmioLdSelected || mmioStoreSelected
  private val mmioChosenBits    = Wire(new MemoryArbiterRoutedReq(targetW))

  mmioChosenBits.target := Mux(
    mmioLdSelected,
    mmioLdArb.io.out.bits.target,
    storeTarget.U(targetW.W)
  )
  mmioChosenBits.req      := Mux(mmioLdSelected, mmioLdArb.io.out.bits.req, sbMmioReq.in.bits)
  mmioChosenBits.req.data := sbMmioReq.in.bits.data

  mmioReq.out.valid := mmioReqValid && mmioRespQ.io.enq.ready
  mmioReq.out.bits  := mmioReqBits.req

  private val mmioIssueFire  = mmioReqValid && mmioReq.out.ready && mmioRespQ.io.enq.ready
  private val mmioStageReady = !mmioReqValid || mmioIssueFire

  mmioLdArb.io.out.ready := mmioStageReady
  sbMmioReq.in.ready     := mmioStageReady && !mmioLdSelected

  mmioRespQ.io.enq.valid := mmioIssueFire
  mmioRespQ.io.enq.bits  := mmioReqBits.target

  when(mmioStageReady) {
    mmioReqValid := mmioChosenValid
    mmioReqBits  := mmioChosenBits
  }

  private val mmioTarget       = mmioRespQ.io.deq.bits
  private val mmioRespValid    = mmioResp.in.valid && mmioRespQ.io.deq.valid
  private val mmioRespReadyVec = Wire(Vec(numReqs, Bool()))

  for (i <- 0 until numLoadPorts) {
    loadMmioResp.out.lanes(i).valid := mmioRespValid && mmioTarget === i.U
    loadMmioResp.out.lanes(i).bits  := mmioResp.in.bits
    mmioRespReadyVec(i)             := loadMmioResp.out.lanes(i).ready
  }

  sbMmioResp.out.valid          := mmioRespValid && mmioTarget === storeTarget.U
  sbMmioResp.out.bits           := mmioResp.in.bits
  mmioRespReadyVec(storeTarget) := sbMmioResp.out.ready

  private val mmioTargetReady = (mmioRespReadyVec.asUInt & UIntToOH(mmioTarget, numReqs)).orR

  mmioResp.in.ready      := mmioRespQ.io.deq.valid && mmioTargetReady
  mmioRespQ.io.deq.ready := mmioResp.in.valid && mmioTargetReady
}
