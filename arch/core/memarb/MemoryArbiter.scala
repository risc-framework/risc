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

  // A single fixed-priority arbiter preserves Load0 > Load1 > Store ordering
  // without a second load-versus-store selection layer. Each load unit holds
  // at most one outstanding operation, so lower-priority ports still make
  // progress while an accepted load waits for its response.
  private val memArb  = Module(new Arbiter(new MemoryArbiterRoutedReq(targetW), numReqs))
  private val mmioArb = Module(new Arbiter(new MemoryArbiterRoutedReq(targetW), numReqs))

  private val memRespQ  = Module(new Queue(UInt(targetW.W), p(RobSize), pipe = false, flow = false))
  private val mmioRespQ = Module(new Queue(UInt(targetW.W), p(RobSize), pipe = false, flow = false))

  private val memReqValid = RegInit(false.B)
  private val memReqBits  = Reg(new MemoryArbiterRoutedReq(targetW))

  private val mmioReqValid = RegInit(false.B)
  private val mmioReqBits  = Reg(new MemoryArbiterRoutedReq(targetW))

  for (i <- 0 until numLoadPorts) {
    memArb.io.in(i).valid        := loadMemReq.in.lanes(i).valid
    memArb.io.in(i).bits.target  := i.U(targetW.W)
    memArb.io.in(i).bits.req     := loadMemReq.in.lanes(i).bits
    loadMemReq.in.lanes(i).ready := memArb.io.in(i).ready

    mmioArb.io.in(i).valid        := loadMmioReq.in.lanes(i).valid
    mmioArb.io.in(i).bits.target  := i.U(targetW.W)
    mmioArb.io.in(i).bits.req     := loadMmioReq.in.lanes(i).bits
    loadMmioReq.in.lanes(i).ready := mmioArb.io.in(i).ready
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

  dcacheReq.out.valid := memReqValid && memRespQ.io.enq.ready
  dcacheReq.out.bits  := memReqBits.req

  private val memIssueFire  = memReqValid && dcacheReq.out.ready && memRespQ.io.enq.ready
  private val memStageReady = !memReqValid || memIssueFire

  memArb.io.out.ready := memStageReady
  sbMemReq.in.ready   := memArb.io.in(storeTarget).ready

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

  mmioArb.io.in(storeTarget).valid       := sbMmioReq.in.valid
  mmioArb.io.in(storeTarget).bits.target := storeTarget.U(targetW.W)
  mmioArb.io.in(storeTarget).bits.req    := sbMmioReq.in.bits

  private val mmioChosenValid = mmioArb.io.out.valid
  private val mmioChosenBits  = Wire(new MemoryArbiterRoutedReq(targetW))

  mmioChosenBits          := mmioArb.io.out.bits
  mmioChosenBits.req.data := sbMmioReq.in.bits.data

  mmioReq.out.valid := mmioReqValid && mmioRespQ.io.enq.ready
  mmioReq.out.bits  := mmioReqBits.req

  private val mmioIssueFire  = mmioReqValid && mmioReq.out.ready && mmioRespQ.io.enq.ready
  private val mmioStageReady = !mmioReqValid || mmioIssueFire

  mmioArb.io.out.ready := mmioStageReady
  sbMmioReq.in.ready   := mmioArb.io.in(storeTarget).ready

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
