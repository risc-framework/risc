package arch.core.lsu

import arch.configs._
import vcache.{ CachePortIO, CacheReq }
import chisel3._
import chisel3.util.{ log2Ceil, RRArbiter, Queue, UIntToOH }
import math.max

class MemoryArbiterRoutedReq(targetWidth: Int)(implicit p: Parameters) extends Bundle {
  val target = UInt(targetWidth.W)
  val req    = new CacheReq(UInt(p(XLen).W), p(L1DCacheParams))
}

class MemoryArbiter(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_memory_arbiter"

  private val NumReqs = p(NumLDs) + 1
  private val TargetW = max(1, log2Ceil(NumReqs))

  val ld_mem  = IO(Vec(p(NumLDs), Flipped(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams)))))
  val ld_mmio = IO(Vec(p(NumLDs), Flipped(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams)))))

  val store_mem  = IO(Flipped(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams))))
  val store_mmio = IO(Flipped(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams))))

  val mem  = IO(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams)))
  val mmio = IO(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams)))

  val memLdArb  = Module(new RRArbiter(new MemoryArbiterRoutedReq(TargetW), p(NumLDs)))
  val mmioLdArb = Module(new RRArbiter(new MemoryArbiterRoutedReq(TargetW), p(NumLDs)))

  val memRespQ  = Module(new Queue(UInt(TargetW.W), p(RobSize), pipe = false, flow = false))
  val mmioRespQ = Module(new Queue(UInt(TargetW.W), p(RobSize), pipe = false, flow = false))

  val memReqValid = RegInit(false.B)
  val memReqBits  = Reg(new MemoryArbiterRoutedReq(TargetW))

  val mmioReqValid = RegInit(false.B)
  val mmioReqBits  = Reg(new MemoryArbiterRoutedReq(TargetW))

  for (i <- 0 until p(NumLDs)) {
    memLdArb.io.in(i).valid       := ld_mem(i).req.valid
    memLdArb.io.in(i).bits.target := i.U(TargetW.W)
    memLdArb.io.in(i).bits.req    := ld_mem(i).req.bits
    ld_mem(i).req.ready           := memLdArb.io.in(i).ready
  }

  val memLdSelected    = memLdArb.io.out.valid
  val memStoreSelected = !memLdSelected && store_mem.req.valid
  val memChosenValid   = memLdSelected || memStoreSelected

  val memChosenBits = Wire(new MemoryArbiterRoutedReq(TargetW))
  memChosenBits.target     := Mux(memLdSelected, memLdArb.io.out.bits.target, p(NumLDs).U(TargetW.W))
  memChosenBits.req.addr   := Mux(
    memLdSelected,
    memLdArb.io.out.bits.req.addr,
    store_mem.req.bits.addr
  )
  memChosenBits.req.data   := Mux(
    memLdSelected,
    memLdArb.io.out.bits.req.data,
    store_mem.req.bits.data
  )
  memChosenBits.req.cmd    := Mux(memLdSelected, memLdArb.io.out.bits.req.cmd, store_mem.req.bits.cmd)
  memChosenBits.req.strb   := Mux(
    memLdSelected,
    memLdArb.io.out.bits.req.strb,
    store_mem.req.bits.strb
  )
  memChosenBits.req.source := Mux(
    memLdSelected,
    memLdArb.io.out.bits.req.source,
    store_mem.req.bits.source
  )

  mem.req.valid := memReqValid && memRespQ.io.enq.ready
  mem.req.bits  := memReqBits.req

  val memIssueFire  = memReqValid && mem.req.ready && memRespQ.io.enq.ready
  val memStageReady = !memReqValid || memIssueFire
  val memTakeFire   = memChosenValid && memStageReady

  memLdArb.io.out.ready := memStageReady
  store_mem.req.ready   := memStageReady && !memLdSelected

  memRespQ.io.enq.valid := memIssueFire
  memRespQ.io.enq.bits  := memReqBits.target

  when(memTakeFire) {
    memReqValid := true.B
    memReqBits  := memChosenBits
  }.elsewhen(memIssueFire) {
    memReqValid := false.B
  }

  val memTarget    = memRespQ.io.deq.bits
  val memRespValid = mem.resp.valid && memRespQ.io.deq.valid

  val memRespReadyVec = Wire(Vec(NumReqs, Bool()))

  for (i <- 0 until p(NumLDs)) {
    ld_mem(i).resp.valid := memRespValid && memTarget === i.U
    ld_mem(i).resp.bits  := mem.resp.bits
    memRespReadyVec(i)   := ld_mem(i).resp.ready
  }

  store_mem.resp.valid       := memRespValid && memTarget === p(NumLDs).U
  store_mem.resp.bits        := mem.resp.bits
  memRespReadyVec(p(NumLDs)) := store_mem.resp.ready

  val memTargetReady = (memRespReadyVec.asUInt & UIntToOH(memTarget, NumReqs)).orR

  mem.resp.ready        := memRespQ.io.deq.valid && memTargetReady
  memRespQ.io.deq.ready := mem.resp.valid && memTargetReady

  for (i <- 0 until p(NumLDs)) {
    mmioLdArb.io.in(i).valid       := ld_mmio(i).req.valid
    mmioLdArb.io.in(i).bits.target := i.U(TargetW.W)
    mmioLdArb.io.in(i).bits.req    := ld_mmio(i).req.bits
    ld_mmio(i).req.ready           := mmioLdArb.io.in(i).ready
  }

  val mmioLdSelected    = mmioLdArb.io.out.valid
  val mmioStoreSelected = !mmioLdSelected && store_mmio.req.valid
  val mmioChosenValid   = mmioLdSelected || mmioStoreSelected

  val mmioChosenBits = Wire(new MemoryArbiterRoutedReq(TargetW))
  mmioChosenBits.target     := Mux(mmioLdSelected, mmioLdArb.io.out.bits.target, p(NumLDs).U(TargetW.W))
  mmioChosenBits.req.addr   := Mux(
    mmioLdSelected,
    mmioLdArb.io.out.bits.req.addr,
    store_mmio.req.bits.addr
  )
  mmioChosenBits.req.data   := Mux(
    mmioLdSelected,
    mmioLdArb.io.out.bits.req.data,
    store_mmio.req.bits.data
  )
  mmioChosenBits.req.cmd    := Mux(
    mmioLdSelected,
    mmioLdArb.io.out.bits.req.cmd,
    store_mmio.req.bits.cmd
  )
  mmioChosenBits.req.strb   := Mux(
    mmioLdSelected,
    mmioLdArb.io.out.bits.req.strb,
    store_mmio.req.bits.strb
  )
  mmioChosenBits.req.source := Mux(
    mmioLdSelected,
    mmioLdArb.io.out.bits.req.source,
    store_mmio.req.bits.source
  )

  mmio.req.valid := mmioReqValid && mmioRespQ.io.enq.ready
  mmio.req.bits  := mmioReqBits.req

  val mmioIssueFire  = mmioReqValid && mmio.req.ready && mmioRespQ.io.enq.ready
  val mmioStageReady = !mmioReqValid || mmioIssueFire
  val mmioTakeFire   = mmioChosenValid && mmioStageReady

  mmioLdArb.io.out.ready := mmioStageReady
  store_mmio.req.ready   := mmioStageReady && !mmioLdSelected

  mmioRespQ.io.enq.valid := mmioIssueFire
  mmioRespQ.io.enq.bits  := mmioReqBits.target

  when(mmioTakeFire) {
    mmioReqValid := true.B
    mmioReqBits  := mmioChosenBits
  }.elsewhen(mmioIssueFire) {
    mmioReqValid := false.B
  }

  val mmioTarget    = mmioRespQ.io.deq.bits
  val mmioRespValid = mmio.resp.valid && mmioRespQ.io.deq.valid

  val mmioRespReadyVec = Wire(Vec(NumReqs, Bool()))

  for (i <- 0 until p(NumLDs)) {
    ld_mmio(i).resp.valid := mmioRespValid && mmioTarget === i.U
    ld_mmio(i).resp.bits  := mmio.resp.bits
    mmioRespReadyVec(i)   := ld_mmio(i).resp.ready
  }

  store_mmio.resp.valid       := mmioRespValid && mmioTarget === p(NumLDs).U
  store_mmio.resp.bits        := mmio.resp.bits
  mmioRespReadyVec(p(NumLDs)) := store_mmio.resp.ready

  val mmioTargetReady = (mmioRespReadyVec.asUInt & UIntToOH(mmioTarget, NumReqs)).orR

  mmio.resp.ready        := mmioRespQ.io.deq.valid && mmioTargetReady
  mmioRespQ.io.deq.ready := mmio.resp.valid && mmioTargetReady
}
