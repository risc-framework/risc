package arch.core.memarb

import arch.configs._
import chisel3._
import chisel3.util.{ Arbiter, Queue, UIntToOH, log2Ceil }
import vutils.graph.Node

class MemoryArbiter(implicit p: Parameters) extends Node[Parameters]("memory_arbiter") {
  val loadMemReq   = inDVec[MemoryArbiterCacheReq](p => p(NumLDs))
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
  private val responseQueueDepth = 1 << log2Ceil(numReqs)

  // Each load unit holds at most one outstanding operation. Fixed priority
  // therefore cannot starve another port indefinitely, and it keeps the
  // request path free of round-robin grant-state feedback.
  private val memLdArb  = Module(new Arbiter(new MemoryArbiterRoutedReq(targetW), numLoadPorts))
  private val mmioLdArb = Module(new Arbiter(new MemoryArbiterRoutedReq(targetW), numLoadPorts))

  // Every requester permits at most one outstanding transaction: one per
  // Load FU plus one StoreBuffer drain. Use the next power of two so the
  // pointer wraps naturally while remaining close to the maximum occupancy.
  private val memRespQ =
    Module(new Queue(UInt(targetW.W), responseQueueDepth, pipe = false, flow = false))
  private val mmioRespQ =
    Module(new Queue(UInt(targetW.W), responseQueueDepth, pipe = false, flow = false))

  private val memReqValid = RegInit(false.B)
  private val memReqBits  = Reg(new MemoryArbiterRoutedReq(targetW))

  private val mmioReqValid = RegInit(false.B)
  private val mmioReqBits  = Reg(new MemoryArbiterRoutedReq(targetW))

  for (i <- 0 until numLoadPorts) {
    memLdArb.io.in(i).valid       := loadMemReq.in.lanes(i).valid
    memLdArb.io.in(i).bits.target := i.U(targetW.W)
    memLdArb.io.in(i).bits.req    := loadMemReq.in.lanes(i).bits
    loadMemReq.in.lanes(i).ready  := memLdArb.io.in(i).ready

    mmioLdArb.io.in(i).valid       := loadMmioReq.in.lanes(i).valid
    mmioLdArb.io.in(i).bits.target := i.U(targetW.W)
    mmioLdArb.io.in(i).bits.req    := loadMmioReq.in.lanes(i).bits
    loadMmioReq.in.lanes(i).ready  := mmioLdArb.io.in(i).ready
  }

  private val memLdSelected    = memLdArb.io.out.valid
  private val memStoreSelected = !memLdSelected && sbMemReq.in.valid
  private val memChosenValid   = memLdSelected || memStoreSelected
  private val memChosenBits    = Wire(new MemoryArbiterRoutedReq(targetW))

  memChosenBits.target := Mux(memLdSelected, memLdArb.io.out.bits.target, storeTarget.U(targetW.W))
  memChosenBits.req    := Mux(memLdSelected, memLdArb.io.out.bits.req, sbMemReq.in.bits)
  // Read data is unused.  Do not let the load-valid arbitration result become
  // a reset/select input on all 32 registered request-data bits.
  memChosenBits.req.data := sbMemReq.in.bits.data

  dcacheReq.out.valid := memReqValid && memRespQ.io.enq.ready
  dcacheReq.out.bits  := memReqBits.req

  private val memIssueFire  = memReqValid && dcacheReq.out.ready && memRespQ.io.enq.ready
  private val memStageReady = !memReqValid || memIssueFire

  memLdArb.io.out.ready := memStageReady
  sbMemReq.in.ready     := memStageReady && !memLdSelected

  memRespQ.io.enq.valid := memIssueFire
  memRespQ.io.enq.bits  := memReqBits.target

  // Keep the stage payload enable independent of upstream request validity.
  // Otherwise synthesis can fold a long upstream request-valid path into the
  // reset/enable pins of every payload register.  Payload contents are don't
  // care while valid is low, so loading them whenever the stage advances is
  // equivalent while leaving validity on the narrow control register only.
  when(memStageReady) {
    memReqValid := memChosenValid
    memReqBits  := memChosenBits
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
