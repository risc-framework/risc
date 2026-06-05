package arch.core.sb

import arch.configs._
import vcache.CacheCommand
import vutils.graph.{ Node, NodeType }
import chisel3._
import chisel3.util.{ Cat, Mux1H, PopCount, log2Ceil }

object StoreBuffer {
  def numLoadPorts(implicit p: Parameters): Int  = p(NumLDs)
  def numStorePorts(implicit p: Parameters): Int = p(NumSTs)
}

class StoreBufferIO(numLoadPorts: Int, numStorePorts: Int)(implicit p: Parameters) extends Bundle {
  val exception = new StoreBufferExceptionIO
  val alloc     = new StoreBufferAllocIO
  val commit    = new StoreBufferCommitIO
  val write     = new StoreBufferWriteIO(numStorePorts)
  val fwd       = new StoreBufferForwardIO(numLoadPorts)
  val state     = new StoreBufferStateIO
  val mem       = new StoreBufferMemIO
}

class StoreBuffer(implicit p: Parameters)
    extends Node(new StoreBufferIO(StoreBuffer.numLoadPorts, StoreBuffer.numStorePorts)) {
  override def nodeType: NodeType  = StoreBufferMeta.Type
  override def desiredName: String = "store_buffer"

  private val numLoadPorts  = StoreBuffer.numLoadPorts
  private val numStorePorts = StoreBuffer.numStorePorts
  private val IdxW          = log2Ceil(p(StoreBufferSize))
  private val CntW          = log2Ceil(p(StoreBufferSize) + 1)

  private def wrapAdd(x: UInt, y: UInt): UInt = {
    val sum = x +& y
    Mux(sum >= p(StoreBufferSize).U, sum - p(StoreBufferSize).U, sum)(IdxW - 1, 0)
  }

  private def zeroEntry: StoreBufferEntry =
    0.U.asTypeOf(new StoreBufferEntry)

  private val entries          = RegInit(VecInit(Seq.fill(p(StoreBufferSize))(zeroEntry)))
  private val head             = RegInit(0.U(IdxW.W))
  private val tail             = RegInit(0.U(IdxW.W))
  private val count            = RegInit(0.U(CntW.W))
  private val tailSeq          = RegInit(0.U(64.W))
  private val drainOutstanding = RegInit(false.B)
  private val drainIsCacheable = RegInit(false.B)

  io.state.tail        := tail
  io.state.tailSeq     := tailSeq
  io.state.freeCount   := p(StoreBufferSize).U(CntW.W) - count
  io.state.empty       := count === 0.U && !drainOutstanding
  io.state.busy        := count =/= 0.U || drainOutstanding
  io.state.oldestValid := count =/= 0.U
  io.state.oldestSeq   := entries(head).seq

  private val allocValid = Wire(Vec(p(IssueWidth), Bool()))

  for (a <- 0 until p(IssueWidth))
    allocValid(a) := io.alloc.ports(a).valid && !io.exception.flush

  for (q <- 0 until numLoadPorts) {
    val fwdRespValid = RegInit(false.B)
    val fwdRespBits  = RegInit(0.U.asTypeOf(new StoreForwardResp))

    io.fwd
      .ports(q)
      .req
      .ready                   := (!fwdRespValid || io.fwd.ports(q).resp.ready) && !io.exception.flush
    io.fwd.ports(q).resp.valid := fwdRespValid && !io.exception.flush
    io.fwd.ports(q).resp.bits  := fwdRespBits

    val req       = io.fwd.ports(q).req.bits
    val reqFire   = io.fwd.ports(q).req.fire
    val dataStage = Wire(Vec(p(StoreBufferSize) + 1, Vec(p(BytesPerWord), UInt(8.W))))
    val maskStage = Wire(Vec(p(StoreBufferSize) + 1, Vec(p(BytesPerWord), Bool())))
    val olderVec  = Wire(Vec(p(StoreBufferSize), Bool()))
    val blockVec  = Wire(Vec(p(StoreBufferSize), Bool()))

    for (b <- 0 until p(BytesPerWord)) {
      dataStage(0)(b) := 0.U
      maskStage(0)(b) := false.B
    }

    for (logical <- 0 until p(StoreBufferSize)) {
      val idx         = wrapAdd(head, logical.U)
      val e           = entries(idx)
      val inRange     = logical.U < count
      val olderLive   = reqFire && req.valid && inRange && e.valid && e.seq < req.sq_seq
      val liveUnknown = olderLive && !e.addrValid
      val forwardable = olderLive && e.addrValid
      val sameLine    = forwardable && e.addr === req.addr

      olderVec(logical) := olderLive
      blockVec(logical) := liveUnknown

      for (b <- 0 until p(BytesPerWord)) {
        val byteHit = sameLine && e.mask(b) && req.mask(b)
        dataStage(logical + 1)(b) := Mux(byteHit, e.data(8 * b + 7, 8 * b), dataStage(logical)(b))
        maskStage(logical + 1)(b) := Mux(byteHit, true.B, maskStage(logical)(b))
      }
    }

    val sameCycleAllocOlder = Wire(Vec(p(IssueWidth), Bool()))

    for (a <- 0 until p(IssueWidth))
      sameCycleAllocOlder(a) := reqFire && req.valid && allocValid(a) && io.alloc
        .ports(a)
        .bits
        .sq_seq < req.sq_seq

    val sameCycleUnknownOlder = sameCycleAllocOlder.asUInt.orR
    val finalMaskVec          = maskStage(p(StoreBufferSize))
    val finalDataVec          = dataStage(p(StoreBufferSize))
    val finalMaskUInt         = finalMaskVec.asUInt
    val reqMask               = req.mask
    val hitMask               = finalMaskUInt & reqMask
    val reqMaskNonZero        = reqMask.orR
    val nextResp              = Wire(new StoreForwardResp)

    nextResp.block     := blockVec.asUInt.orR || sameCycleUnknownOlder
    nextResp.has_older := olderVec.asUInt.orR || sameCycleUnknownOlder
    nextResp.valid     := reqFire && req.valid && reqMaskNonZero && !sameCycleUnknownOlder && hitMask.orR
    nextResp.full      := reqFire && req.valid && reqMaskNonZero && !sameCycleUnknownOlder && hitMask === reqMask
    nextResp.data      := Cat((p(BytesPerWord) - 1 to 0 by -1).map(i => finalDataVec(i)))
    nextResp.mask      := finalMaskUInt

    when(io.exception.flush) {
      fwdRespValid := false.B
      fwdRespBits  := 0.U.asTypeOf(new StoreForwardResp)
    }.otherwise {
      when(reqFire) {
        fwdRespValid := true.B
        fwdRespBits  := nextResp
      }.elsewhen(io.fwd.ports(q).resp.fire) {
        fwdRespValid := false.B
      }
    }
  }

  private val headEntry = entries(head)
  private val canDrain  =
    headEntry.valid && headEntry.committed && headEntry.addrValid && !drainOutstanding

  io.mem.mem.req.valid       := canDrain && headEntry.cacheable
  io.mem.mem.req.bits.cmd    := CacheCommand.Write
  io.mem.mem.req.bits.addr   := headEntry.addr
  io.mem.mem.req.bits.data   := headEntry.data
  io.mem.mem.req.bits.strb   := headEntry.mask
  io.mem.mem.req.bits.source := 0.U

  io.mem.mmio.req.valid       := canDrain && !headEntry.cacheable
  io.mem.mmio.req.bits.cmd    := CacheCommand.Write
  io.mem.mmio.req.bits.addr   := headEntry.addr
  io.mem.mmio.req.bits.data   := headEntry.data
  io.mem.mmio.req.bits.strb   := headEntry.mask
  io.mem.mmio.req.bits.source := 0.U

  io.mem.mem.resp.ready  := drainOutstanding && drainIsCacheable
  io.mem.mmio.resp.ready := drainOutstanding && !drainIsCacheable

  private val drainReqFire  = io.mem.mem.req.fire || io.mem.mmio.req.fire
  private val drainRespFire = io.mem.mem.resp.fire || io.mem.mmio.resp.fire

  private val allocCount      = PopCount(allocValid)
  private val afterDrainHead  = Mux(drainRespFire, wrapAdd(head, 1.U), head)
  private val normalTail      = wrapAdd(tail, allocCount)
  private val normalCountWide = count +& allocCount - drainRespFire.asUInt
  private val normalCount     = normalCountWide(CntW - 1, 0)
  private val normalSeq       = tailSeq + allocCount

  private val afterOpsEntries = Wire(Vec(p(StoreBufferSize), new StoreBufferEntry))

  for (i <- 0 until p(StoreBufferSize)) {
    val drainedThis = drainRespFire && head === i.U
    val writeHit    = Wire(Vec(numStorePorts, Bool()))
    val commitHit   = Wire(Vec(p(IssueWidth), Bool()))
    val allocHit    = Wire(Vec(p(IssueWidth), Bool()))

    for (s <- 0 until numStorePorts)
      writeHit(s) := io.write.ports(s).valid && io.write.ports(s).bits.sq_idx === i.U && entries(
        i
      ).valid && entries(i).rob_tag === io.write.ports(s).bits.rob_tag && !drainedThis

    for (c <- 0 until p(IssueWidth))
      commitHit(c) := io.commit.ports(c).valid && io.commit.ports(c).bits === i.U && entries(
        i
      ).valid && !drainedThis

    for (a <- 0 until p(IssueWidth))
      allocHit(a) := allocValid(a) && io.alloc.ports(a).bits.sq_idx === i.U

    val anyWrite       = writeHit.asUInt.orR
    val anyCommit      = commitHit.asUInt.orR
    val anyAlloc       = allocHit.asUInt.orR
    val writeAddr      = Mux1H(
      (0 until numStorePorts).map(s => writeHit(s) -> io.write.ports(s).bits.addr)
    )
    val writeData      = Mux1H(
      (0 until numStorePorts).map(s => writeHit(s) -> io.write.ports(s).bits.data)
    )
    val writeMask      = Mux1H(
      (0 until numStorePorts).map(s => writeHit(s) -> io.write.ports(s).bits.mask)
    )
    val writeCacheable = Mux1H(
      (0 until numStorePorts).map(s => writeHit(s) -> io.write.ports(s).bits.cacheable)
    )
    val allocSeq       = Mux1H(
      (0 until p(IssueWidth)).map(a => allocHit(a) -> io.alloc.ports(a).bits.sq_seq)
    )
    val allocRobTag    = Mux1H(
      (0 until p(IssueWidth)).map(a => allocHit(a) -> io.alloc.ports(a).bits.rob_tag)
    )
    val e              = Wire(new StoreBufferEntry)

    e := entries(i)

    when(drainedThis) {
      e := zeroEntry
    }.elsewhen(anyAlloc) {
      e.valid     := true.B
      e.committed := false.B
      e.addrValid := false.B
      e.fwdValid  := false.B
      e.seq       := allocSeq
      e.rob_tag   := allocRobTag
      e.addr      := 0.U
      e.data      := 0.U
      e.mask      := 0.U
      e.cacheable := false.B
    }.otherwise {
      when(anyWrite) {
        e.addrValid := true.B
        e.fwdValid  := true.B
        e.addr      := writeAddr
        e.data      := writeData
        e.mask      := writeMask
        e.cacheable := writeCacheable
      }

      when(anyCommit) {
        e.committed := true.B
      }
    }

    afterOpsEntries(i) := e
  }

  private val keepPrefix  = Wire(Vec(p(StoreBufferSize), Bool()))
  private val prefixAlive = Wire(Vec(p(StoreBufferSize) + 1, Bool()))

  prefixAlive(0) := true.B

  for (logical <- 0 until p(StoreBufferSize)) {
    val idx     = wrapAdd(afterDrainHead, logical.U)
    val e       = afterOpsEntries(idx)
    val inRange = logical.U < normalCount

    keepPrefix(logical)      := prefixAlive(logical) && inRange && e.valid && e.committed
    prefixAlive(logical + 1) := keepPrefix(logical)
  }

  private val flushCount   = PopCount(keepPrefix)
  private val flushTail    = wrapAdd(afterDrainHead, flushCount)
  private val keepPhysical = Wire(Vec(p(StoreBufferSize), Bool()))

  for (i <- 0 until p(StoreBufferSize)) {
    val keepHits = Wire(Vec(p(StoreBufferSize), Bool()))

    for (logical <- 0 until p(StoreBufferSize))
      keepHits(logical) := keepPrefix(logical) && wrapAdd(afterDrainHead, logical.U) === i.U

    keepPhysical(i) := keepHits.asUInt.orR
  }

  for (i <- 0 until p(StoreBufferSize))
    when(io.exception.flush && !keepPhysical(i)) {
      entries(i) := zeroEntry
    }.otherwise {
      entries(i) := afterOpsEntries(i)
    }

  head    := afterDrainHead
  tail    := Mux(io.exception.flush, flushTail, normalTail)
  count   := Mux(io.exception.flush, flushCount, normalCount)
  tailSeq := normalSeq

  when(drainReqFire) {
    drainOutstanding := true.B
    drainIsCacheable := headEntry.cacheable
  }

  when(drainRespFire) {
    drainOutstanding := false.B
  }
}
