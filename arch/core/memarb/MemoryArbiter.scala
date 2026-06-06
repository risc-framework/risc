package arch.core.memarb

import arch.configs._
import vcache.{ CachePortIO, CacheReq }
import vutils.graph.{ Node, NodeType }
import chisel3._
import chisel3.util.{ Queue, RRArbiter, UIntToOH, log2Ceil }

class MemoryArbiterIO(implicit p: Parameters) extends Bundle {
  val fu_pool = new MemoryArbiterFuPoolIO
  val sb      = new MemoryArbiterSbIO
  val dcache  = new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams))
  val mmio    = new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams))
}

class MemoryArbiterRoutedReq(targetWidth: Int)(implicit p: Parameters) extends Bundle {
  val target = UInt(targetWidth.W)
  val req    = new CacheReq(UInt(p(XLen).W), p(L1DCacheParams))
}

class MemoryArbiter(implicit p: Parameters) extends Node(new MemoryArbiterIO) {
  override def nodeType: NodeType  = MemoryArbiterMeta.Type
  override def desiredName: String = "memory_arbiter"

  private val numLoadPorts = p(NumLDs)

  private val numReqs     = numLoadPorts + 1
  private val TargetW     = log2Ceil(numReqs).max(1)
  private val storeTarget = numLoadPorts

  private val memLdArb  = Module(new RRArbiter(new MemoryArbiterRoutedReq(TargetW), numLoadPorts))
  private val mmioLdArb = Module(new RRArbiter(new MemoryArbiterRoutedReq(TargetW), numLoadPorts))

  private val memRespQ  = Module(new Queue(UInt(TargetW.W), p(RobSize), pipe = false, flow = false))
  private val mmioRespQ = Module(new Queue(UInt(TargetW.W), p(RobSize), pipe = false, flow = false))

  private val memReqValid = RegInit(false.B)
  private val memReqBits  = Reg(new MemoryArbiterRoutedReq(TargetW))

  private val mmioReqValid = RegInit(false.B)
  private val mmioReqBits  = Reg(new MemoryArbiterRoutedReq(TargetW))

  for (i <- 0 until numLoadPorts) {
    memLdArb.io.in(i).valid          := io.fu_pool.load_mem(i).req.valid
    memLdArb.io.in(i).bits.target    := i.U(TargetW.W)
    memLdArb.io.in(i).bits.req       := io.fu_pool.load_mem(i).req.bits
    io.fu_pool.load_mem(i).req.ready := memLdArb.io.in(i).ready

    mmioLdArb.io.in(i).valid          := io.fu_pool.load_mmio(i).req.valid
    mmioLdArb.io.in(i).bits.target    := i.U(TargetW.W)
    mmioLdArb.io.in(i).bits.req       := io.fu_pool.load_mmio(i).req.bits
    io.fu_pool.load_mmio(i).req.ready := mmioLdArb.io.in(i).ready
  }

  private val memLdSelected    = memLdArb.io.out.valid
  private val memStoreSelected = !memLdSelected && io.sb.mem.req.valid
  private val memChosenValid   = memLdSelected || memStoreSelected
  private val memChosenBits    = Wire(new MemoryArbiterRoutedReq(TargetW))

  memChosenBits.target     := Mux(memLdSelected, memLdArb.io.out.bits.target, storeTarget.U(TargetW.W))
  memChosenBits.req.addr   := Mux(
    memLdSelected,
    memLdArb.io.out.bits.req.addr,
    io.sb.mem.req.bits.addr
  )
  memChosenBits.req.data   := Mux(
    memLdSelected,
    memLdArb.io.out.bits.req.data,
    io.sb.mem.req.bits.data
  )
  memChosenBits.req.cmd    := Mux(memLdSelected, memLdArb.io.out.bits.req.cmd, io.sb.mem.req.bits.cmd)
  memChosenBits.req.strb   := Mux(
    memLdSelected,
    memLdArb.io.out.bits.req.strb,
    io.sb.mem.req.bits.strb
  )
  memChosenBits.req.source := Mux(
    memLdSelected,
    memLdArb.io.out.bits.req.source,
    io.sb.mem.req.bits.source
  )

  io.dcache.req.valid := memReqValid && memRespQ.io.enq.ready
  io.dcache.req.bits  := memReqBits.req

  private val memIssueFire  = memReqValid && io.dcache.req.ready && memRespQ.io.enq.ready
  private val memStageReady = !memReqValid || memIssueFire
  private val memTakeFire   = memChosenValid && memStageReady

  memLdArb.io.out.ready := memStageReady
  io.sb.mem.req.ready   := memStageReady && !memLdSelected

  memRespQ.io.enq.valid := memIssueFire
  memRespQ.io.enq.bits  := memReqBits.target

  when(memTakeFire) {
    memReqValid := true.B
    memReqBits  := memChosenBits
  }.elsewhen(memIssueFire) {
    memReqValid := false.B
  }

  private val memTarget       = memRespQ.io.deq.bits
  private val memRespValid    = io.dcache.resp.valid && memRespQ.io.deq.valid
  private val memRespReadyVec = Wire(Vec(numReqs, Bool()))

  for (i <- 0 until numLoadPorts) {
    io.fu_pool.load_mem(i).resp.valid := memRespValid && memTarget === i.U
    io.fu_pool.load_mem(i).resp.bits  := io.dcache.resp.bits
    memRespReadyVec(i)                := io.fu_pool.load_mem(i).resp.ready
  }

  io.sb.mem.resp.valid         := memRespValid && memTarget === storeTarget.U
  io.sb.mem.resp.bits          := io.dcache.resp.bits
  memRespReadyVec(storeTarget) := io.sb.mem.resp.ready

  private val memTargetReady = (memRespReadyVec.asUInt & UIntToOH(memTarget, numReqs)).orR

  io.dcache.resp.ready  := memRespQ.io.deq.valid && memTargetReady
  memRespQ.io.deq.ready := io.dcache.resp.valid && memTargetReady

  private val mmioLdSelected    = mmioLdArb.io.out.valid
  private val mmioStoreSelected = !mmioLdSelected && io.sb.mmio.req.valid
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
    io.sb.mmio.req.bits.addr
  )
  mmioChosenBits.req.data   := Mux(
    mmioLdSelected,
    mmioLdArb.io.out.bits.req.data,
    io.sb.mmio.req.bits.data
  )
  mmioChosenBits.req.cmd    := Mux(
    mmioLdSelected,
    mmioLdArb.io.out.bits.req.cmd,
    io.sb.mmio.req.bits.cmd
  )
  mmioChosenBits.req.strb   := Mux(
    mmioLdSelected,
    mmioLdArb.io.out.bits.req.strb,
    io.sb.mmio.req.bits.strb
  )
  mmioChosenBits.req.source := Mux(
    mmioLdSelected,
    mmioLdArb.io.out.bits.req.source,
    io.sb.mmio.req.bits.source
  )

  io.mmio.req.valid := mmioReqValid && mmioRespQ.io.enq.ready
  io.mmio.req.bits  := mmioReqBits.req

  private val mmioIssueFire  = mmioReqValid && io.mmio.req.ready && mmioRespQ.io.enq.ready
  private val mmioStageReady = !mmioReqValid || mmioIssueFire
  private val mmioTakeFire   = mmioChosenValid && mmioStageReady

  mmioLdArb.io.out.ready := mmioStageReady
  io.sb.mmio.req.ready   := mmioStageReady && !mmioLdSelected

  mmioRespQ.io.enq.valid := mmioIssueFire
  mmioRespQ.io.enq.bits  := mmioReqBits.target

  when(mmioTakeFire) {
    mmioReqValid := true.B
    mmioReqBits  := mmioChosenBits
  }.elsewhen(mmioIssueFire) {
    mmioReqValid := false.B
  }

  private val mmioTarget       = mmioRespQ.io.deq.bits
  private val mmioRespValid    = io.mmio.resp.valid && mmioRespQ.io.deq.valid
  private val mmioRespReadyVec = Wire(Vec(numReqs, Bool()))

  for (i <- 0 until numLoadPorts) {
    io.fu_pool.load_mmio(i).resp.valid := mmioRespValid && mmioTarget === i.U
    io.fu_pool.load_mmio(i).resp.bits  := io.mmio.resp.bits
    mmioRespReadyVec(i)                := io.fu_pool.load_mmio(i).resp.ready
  }

  io.sb.mmio.resp.valid         := mmioRespValid && mmioTarget === storeTarget.U
  io.sb.mmio.resp.bits          := io.mmio.resp.bits
  mmioRespReadyVec(storeTarget) := io.sb.mmio.resp.ready

  private val mmioTargetReady = (mmioRespReadyVec.asUInt & UIntToOH(mmioTarget, numReqs)).orR

  io.mmio.resp.ready     := mmioRespQ.io.deq.valid && mmioTargetReady
  mmioRespQ.io.deq.ready := io.mmio.resp.valid && mmioTargetReady
}
