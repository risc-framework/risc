package arch.node.memarb

import arch.core.fu.FunctionalUnitType
import arch.configs._
import vutils.graph.{ Node, NodeType }
import chisel3._
import chisel3.util.{ Mux1H, RRArbiter, log2Ceil }

class MemoryArbiter(implicit p: Parameters) extends Node(new MemoryArbiterIO) {
  override def nodeType: NodeType  = MemoryArbiterMeta.Type
  override def desiredName: String = s"memory_arbiter_${p(ISA).name}"

  private val numLoadPorts =
    p(FunctionalUnits).count(_.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD)
  private val numReqs      = numLoadPorts + 1
  private val TargetW      = log2Ceil(numReqs).max(1)

  private val memReqArb  = Module(new RRArbiter(chiselTypeOf(io.out.mem.req.bits), numReqs))
  private val mmioReqArb = Module(new RRArbiter(chiselTypeOf(io.out.mmio.req.bits), numReqs))

  for (i <- 0 until numLoadPorts) {
    // NOTE: source always 0
    memReqArb.io.in(i).valid       := io.load.mem(i).req.valid
    memReqArb.io.in(i).bits        := io.load.mem(i).req.bits
    memReqArb.io.in(i).bits.source := 0.U(TargetW.W)
    io.load.mem(i).req.ready       := memReqArb.io.in(i).ready

    mmioReqArb.io.in(i).valid       := io.load.mmio(i).req.valid
    mmioReqArb.io.in(i).bits        := io.load.mmio(i).req.bits
    mmioReqArb.io.in(i).bits.source := 0.U(TargetW.W)
    io.load.mmio(i).req.ready       := mmioReqArb.io.in(i).ready
  }

  private val storeTarget = numLoadPorts

  memReqArb.io.in(storeTarget).valid       := io.store.mem.req.valid
  memReqArb.io.in(storeTarget).bits        := io.store.mem.req.bits
  memReqArb.io.in(storeTarget).bits.source := storeTarget.U(TargetW.W)
  io.store.mem.req.ready                   := memReqArb.io.in(storeTarget).ready

  mmioReqArb.io.in(storeTarget).valid       := io.store.mmio.req.valid
  mmioReqArb.io.in(storeTarget).bits        := io.store.mmio.req.bits
  mmioReqArb.io.in(storeTarget).bits.source := storeTarget.U(TargetW.W)
  io.store.mmio.req.ready                   := mmioReqArb.io.in(storeTarget).ready

  io.out.mem.req <> memReqArb.io.out
  io.out.mmio.req <> mmioReqArb.io.out

  private val memRespTarget  = io.out.mem.resp.bits.source
  private val mmioRespTarget = io.out.mmio.resp.bits.source

  for (i <- 0 until numLoadPorts) {
    io.load.mem(i).resp.valid  := io.out.mem.resp.valid && memRespTarget === i.U
    io.load.mem(i).resp.bits   := io.out.mem.resp.bits
    io.load.mmio(i).resp.valid := io.out.mmio.resp.valid && mmioRespTarget === i.U
    io.load.mmio(i).resp.bits  := io.out.mmio.resp.bits
  }

  io.store.mem.resp.valid  := io.out.mem.resp.valid && memRespTarget === storeTarget.U
  io.store.mem.resp.bits   := io.out.mem.resp.bits
  io.store.mmio.resp.valid := io.out.mmio.resp.valid && mmioRespTarget === storeTarget.U
  io.store.mmio.resp.bits  := io.out.mmio.resp.bits

  io.out.mem.resp.ready := Mux1H(
    (0 until numLoadPorts).map(i => (memRespTarget === i.U) -> io.load.mem(i).resp.ready) ++
      Seq((memRespTarget === storeTarget.U) -> io.store.mem.resp.ready)
  )

  io.out.mmio.resp.ready := Mux1H(
    (0 until numLoadPorts).map(i => (mmioRespTarget === i.U) -> io.load.mmio(i).resp.ready) ++
      Seq((mmioRespTarget === storeTarget.U) -> io.store.mmio.resp.ready)
  )
}
