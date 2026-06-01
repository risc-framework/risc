package arch.node.memarb

import arch.core.fu.FunctionalUnitType
import arch.configs._
import vcache.CacheReq
import vutils.graph.{ Node, NodeType }
import chisel3._
import chisel3.util.{ Queue, RRArbiter, UIntToOH, log2Ceil }

class MemoryArbiterRoutedReq(targetWidth: Int)(implicit p: Parameters) extends Bundle {
  val target = UInt(targetWidth.W)
  val req    = new CacheReq(UInt(p(XLen).W), p(L1DCacheParams))
}

class MemoryArbiter(implicit p: Parameters) extends Node(new MemoryArbiterIO) {
  override def nodeType: NodeType  = MemoryArbiterMeta.Type
  override def desiredName: String = s"memory_arbiter_${p(ISA).name}"

  private val numLoadPorts =
    p(FunctionalUnits).count(_.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD)
  private val numReqs      = numLoadPorts + 1
  private val TargetW      = log2Ceil(numReqs).max(1)
  private val storeTarget  = numLoadPorts

  private val memLdArb  = Module(new RRArbiter(new MemoryArbiterRoutedReq(TargetW), numLoadPorts))
  private val mmioLdArb = Module(new RRArbiter(new MemoryArbiterRoutedReq(TargetW), numLoadPorts))

  private val memRespQ  = Module(new Queue(UInt(TargetW.W), p(RobSize), pipe = false, flow = false))
  private val mmioRespQ = Module(new Queue(UInt(TargetW.W), p(RobSize), pipe = false, flow = false))

  private val memReqValid = RegInit(false.B)
  private val memReqBits  = Reg(new MemoryArbiterRoutedReq(TargetW))

  private val mmioReqValid = RegInit(false.B)
  private val mmioReqBits  = Reg(new MemoryArbiterRoutedReq(TargetW))

  for (i <- 0 until numLoadPorts) {
    memLdArb.io.in(i).valid       := io.load.mem(i).req.valid
    memLdArb.io.in(i).bits.target := i.U(TargetW.W)
    memLdArb.io.in(i).bits.req    := io.load.mem(i).req.bits
    io.load.mem(i).req.ready      := memLdArb.io.in(i).ready

    mmioLdArb.io.in(i).valid       := io.load.mmio(i).req.valid
    mmioLdArb.io.in(i).bits.target := i.U(TargetW.W)
    mmioLdArb.io.in(i).bits.req    := io.load.mmio(i).req.bits
    io.load.mmio(i).req.ready      := mmioLdArb.io.in(i).ready
  }

  private val memLdSelected    = memLdArb.io.out.valid
  private val memStoreSelected = !memLdSelected && io.store.mem.req.valid
  private val memChosenValid   = memLdSelected || memStoreSelected
  private val memChosenBits    = Wire(new MemoryArbiterRoutedReq(TargetW))

  memChosenBits.target     := Mux(memLdSelected, memLdArb.io.out.bits.target, storeTarget.U(TargetW.W))
  memChosenBits.req.addr   := Mux(
    memLdSelected,
    memLdArb.io.out.bits.req.addr,
    io.store.mem.req.bits.addr
  )
  memChosenBits.req.data   := Mux(
    memLdSelected,
    memLdArb.io.out.bits.req.data,
    io.store.mem.req.bits.data
  )
  memChosenBits.req.cmd    := Mux(
    memLdSelected,
    memLdArb.io.out.bits.req.cmd,
    io.store.mem.req.bits.cmd
  )
  memChosenBits.req.strb   := Mux(
    memLdSelected,
    memLdArb.io.out.bits.req.strb,
    io.store.mem.req.bits.strb
  )
  memChosenBits.req.source := Mux(
    memLdSelected,
    memLdArb.io.out.bits.req.source,
    io.store.mem.req.bits.source
  )

  io.out.mem.req.valid := memReqValid && memRespQ.io.enq.ready
  io.out.mem.req.bits  := memReqBits.req

  private val memIssueFire  = memReqValid && io.out.mem.req.ready && memRespQ.io.enq.ready
  private val memStageReady = !memReqValid || memIssueFire
  private val memTakeFire   = memChosenValid && memStageReady

  memLdArb.io.out.ready  := memStageReady
  io.store.mem.req.ready := memStageReady && !memLdSelected

  memRespQ.io.enq.valid := memIssueFire
  memRespQ.io.enq.bits  := memReqBits.target

  when(memTakeFire) {
    memReqValid := true.B
    memReqBits  := memChosenBits
  }.elsewhen(memIssueFire) {
    memReqValid := false.B
  }

  private val memTarget       = memRespQ.io.deq.bits
  private val memRespValid    = io.out.mem.resp.valid && memRespQ.io.deq.valid
  private val memRespReadyVec = Wire(Vec(numReqs, Bool()))

  for (i <- 0 until numLoadPorts) {
    io.load.mem(i).resp.valid := memRespValid && memTarget === i.U
    io.load.mem(i).resp.bits  := io.out.mem.resp.bits
    memRespReadyVec(i)        := io.load.mem(i).resp.ready
  }

  io.store.mem.resp.valid      := memRespValid && memTarget === storeTarget.U
  io.store.mem.resp.bits       := io.out.mem.resp.bits
  memRespReadyVec(storeTarget) := io.store.mem.resp.ready

  private val memTargetReady = (memRespReadyVec.asUInt & UIntToOH(memTarget, numReqs)).orR

  io.out.mem.resp.ready := memRespQ.io.deq.valid && memTargetReady
  memRespQ.io.deq.ready := io.out.mem.resp.valid && memTargetReady

  private val mmioLdSelected    = mmioLdArb.io.out.valid
  private val mmioStoreSelected = !mmioLdSelected && io.store.mmio.req.valid
  private val mmioChosenValid   = mmioLdSelected || mmioStoreSelected
  private val mmioChosenBits    = Wire(new MemoryArbiterRoutedReq(TargetW))

  mmioChosenBits.target     := Mux(
    mmioLdSelected,
    mmioLdArb.io.out.bits.target,
    storeTarget.U(TargetW.W)
  )
  mmioChosenBits.req.addr   := Mux(
    mmioLdSelected,
    mmioLdArb.io.out.bits.req.addr,
    io.store.mmio.req.bits.addr
  )
  mmioChosenBits.req.data   := Mux(
    mmioLdSelected,
    mmioLdArb.io.out.bits.req.data,
    io.store.mmio.req.bits.data
  )
  mmioChosenBits.req.cmd    := Mux(
    mmioLdSelected,
    mmioLdArb.io.out.bits.req.cmd,
    io.store.mmio.req.bits.cmd
  )
  mmioChosenBits.req.strb   := Mux(
    mmioLdSelected,
    mmioLdArb.io.out.bits.req.strb,
    io.store.mmio.req.bits.strb
  )
  mmioChosenBits.req.source := Mux(
    mmioLdSelected,
    mmioLdArb.io.out.bits.req.source,
    io.store.mmio.req.bits.source
  )

  io.out.mmio.req.valid := mmioReqValid && mmioRespQ.io.enq.ready
  io.out.mmio.req.bits  := mmioReqBits.req

  private val mmioIssueFire  = mmioReqValid && io.out.mmio.req.ready && mmioRespQ.io.enq.ready
  private val mmioStageReady = !mmioReqValid || mmioIssueFire
  private val mmioTakeFire   = mmioChosenValid && mmioStageReady

  mmioLdArb.io.out.ready  := mmioStageReady
  io.store.mmio.req.ready := mmioStageReady && !mmioLdSelected

  mmioRespQ.io.enq.valid := mmioIssueFire
  mmioRespQ.io.enq.bits  := mmioReqBits.target

  when(mmioTakeFire) {
    mmioReqValid := true.B
    mmioReqBits  := mmioChosenBits
  }.elsewhen(mmioIssueFire) {
    mmioReqValid := false.B
  }

  private val mmioTarget       = mmioRespQ.io.deq.bits
  private val mmioRespValid    = io.out.mmio.resp.valid && mmioRespQ.io.deq.valid
  private val mmioRespReadyVec = Wire(Vec(numReqs, Bool()))

  for (i <- 0 until numLoadPorts) {
    io.load.mmio(i).resp.valid := mmioRespValid && mmioTarget === i.U
    io.load.mmio(i).resp.bits  := io.out.mmio.resp.bits
    mmioRespReadyVec(i)        := io.load.mmio(i).resp.ready
  }

  io.store.mmio.resp.valid      := mmioRespValid && mmioTarget === storeTarget.U
  io.store.mmio.resp.bits       := io.out.mmio.resp.bits
  mmioRespReadyVec(storeTarget) := io.store.mmio.resp.ready

  private val mmioTargetReady = (mmioRespReadyVec.asUInt & UIntToOH(mmioTarget, numReqs)).orR

  io.out.mmio.resp.ready := mmioRespQ.io.deq.valid && mmioTargetReady
  mmioRespQ.io.deq.ready := io.out.mmio.resp.valid && mmioTargetReady
}
