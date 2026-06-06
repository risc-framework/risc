package arch.core.sb

import arch.core.dispatch.DispatchStoreBufferIO
import arch.core.rob.RobSbIO
import arch.configs._
import vcache.CacheCommand
import vutils.graph.{ Node, NodeType }
import chisel3._
import chisel3.util.{ Cat, Mux1H, PopCount, log2Ceil }

class StoreBufferIO(implicit p: Parameters) extends Bundle {
  val exception      = new StoreBufferExceptionIO
  val dispatch       = Flipped(new DispatchStoreBufferIO)
  val rob            = Flipped(new RobSbIO)
  val fu_pool        = new StoreBufferFuPoolIO
  val memory_arbiter = new StoreBufferMemoryArbiterIO
}

class StoreBuffer(implicit p: Parameters) extends Node(new StoreBufferIO) {
  override def nodeType: NodeType  = StoreBufferMeta.Type
  override def desiredName: String = "store_buffer"

  private val numLoadPorts  = p(NumLDs)
  private val numStorePorts = p(NumSTs)
  private val idxW          = log2Ceil(p(StoreBufferSize))
  private val cntW          = log2Ceil(p(StoreBufferSize) + 1)

  private def wrapAdd(x: UInt, y: UInt): UInt = {
    val sum = x +& y
    Mux(sum >= p(StoreBufferSize).U, sum - p(StoreBufferSize).U, sum)(idxW - 1, 0)
  }

  private def zeroEntry: StoreBufferEntry =
    0.U.asTypeOf(new StoreBufferEntry)

  private val entries          = RegInit(VecInit(Seq.fill(p(StoreBufferSize))(zeroEntry)))
  private val head             = RegInit(0.U(idxW.W))
  private val tail             = RegInit(0.U(idxW.W))
  private val count            = RegInit(0.U(cntW.W))
  private val tailSeq          = RegInit(0.U(64.W))
  private val drainOutstanding = RegInit(false.B)
  private val drainIsCacheable = RegInit(false.B)

  private val freeCount = p(StoreBufferSize).U(cntW.W) - count

  io.fu_pool.oldest_valid := count =/= 0.U
  io.fu_pool.oldest_seq   := entries(head).seq

  private val laneIsStore = Wire(Vec(p(IssueWidth), Bool()))

  for (w <- 0 until p(IssueWidth))
    laneIsStore(w) := io.dispatch.lanes(w).valid && io.dispatch.lanes(w).bits.isStore && !io.exception.flush

  private val possibleStoreBeforeOrAt = Wire(
    Vec(p(IssueWidth), UInt(log2Ceil(p(IssueWidth) + 1).W))
  )

  for (w <- 0 until p(IssueWidth))
    possibleStoreBeforeOrAt(w) := PopCount((0 to w).map(i => laneIsStore(i)))

  private val sqIdxForLane = Wire(Vec(p(IssueWidth), UInt(idxW.W)))
  private val sqTailAfter  = Wire(Vec(p(IssueWidth) + 1, UInt(idxW.W)))
  private val sqSeqForLane = Wire(Vec(p(IssueWidth), UInt(64.W)))
  private val sqSeqAfter   = Wire(Vec(p(IssueWidth) + 1, UInt(64.W)))
  private val allocValid   = Wire(Vec(p(IssueWidth), Bool()))

  sqTailAfter(0) := tail
  sqSeqAfter(0)  := tailSeq

  for (w <- 0 until p(IssueWidth)) {
    val canReserve = !laneIsStore(w) || possibleStoreBeforeOrAt(w) <= freeCount
    val allocStore = io.dispatch.lanes(w).fire && io.dispatch.lanes(w).bits.isStore && !io.exception.flush

    io.dispatch.lanes(w).ready         := canReserve
    io.dispatch.lanes(w).ticket.sq_idx := sqTailAfter(w)
    io.dispatch.lanes(w).ticket.sq_seq := sqSeqAfter(w)

    sqIdxForLane(w) := sqTailAfter(w)
    sqSeqForLane(w) := sqSeqAfter(w)

    sqTailAfter(w + 1) := Mux(allocStore, wrapAdd(sqTailAfter(w), 1.U), sqTailAfter(w))
    sqSeqAfter(w + 1)  := sqSeqAfter(w) + allocStore.asUInt
    allocValid(w)      := allocStore
  }

  for (q <- 0 until numLoadPorts) {
    val fwdRespValid = RegInit(false.B)
    val fwdRespBits  = RegInit(0.U.asTypeOf(new StoreForwardResp))

    io.fu_pool.fwd(q).req.ready  := (!fwdRespValid || io.fu_pool.fwd(q).resp.ready) && !io.exception.flush
    io.fu_pool.fwd(q).resp.valid := fwdRespValid && !io.exception.flush
    io.fu_pool.fwd(q).resp.bits  := fwdRespBits

    val req       = io.fu_pool.fwd(q).req.bits
    val reqFire   = io.fu_pool.fwd(q).req.fire
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
      sameCycleAllocOlder(a) := reqFire && req.valid && allocValid(a) && sqSeqForLane(a) < req.sq_seq

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
      }.elsewhen(io.fu_pool.fwd(q).resp.fire) {
        fwdRespValid := false.B
      }
    }
  }

  private val headEntry = entries(head)
  private val canDrain  =
    headEntry.valid && headEntry.committed && headEntry.addrValid && !drainOutstanding

  io.memory_arbiter.mem.req.valid       := canDrain && headEntry.cacheable
  io.memory_arbiter.mem.req.bits.cmd    := CacheCommand.Write
  io.memory_arbiter.mem.req.bits.addr   := headEntry.addr
  io.memory_arbiter.mem.req.bits.data   := headEntry.data
  io.memory_arbiter.mem.req.bits.strb   := headEntry.mask
  io.memory_arbiter.mem.req.bits.source := 0.U

  io.memory_arbiter.mmio.req.valid       := canDrain && !headEntry.cacheable
  io.memory_arbiter.mmio.req.bits.cmd    := CacheCommand.Write
  io.memory_arbiter.mmio.req.bits.addr   := headEntry.addr
  io.memory_arbiter.mmio.req.bits.data   := headEntry.data
  io.memory_arbiter.mmio.req.bits.strb   := headEntry.mask
  io.memory_arbiter.mmio.req.bits.source := 0.U

  io.memory_arbiter.mem.resp.ready  := drainOutstanding && drainIsCacheable
  io.memory_arbiter.mmio.resp.ready := drainOutstanding && !drainIsCacheable

  private val drainReqFire  = io.memory_arbiter.mem.req.fire || io.memory_arbiter.mmio.req.fire
  private val drainRespFire = io.memory_arbiter.mem.resp.fire || io.memory_arbiter.mmio.resp.fire

  private val allocCount      = PopCount(allocValid)
  private val afterDrainHead  = Mux(drainRespFire, wrapAdd(head, 1.U), head)
  private val normalTail      = wrapAdd(tail, allocCount)
  private val normalCountWide = count +& allocCount - drainRespFire.asUInt
  private val normalCount     = normalCountWide(cntW - 1, 0)
  private val normalSeq       = tailSeq + allocCount

  private val afterOpsEntries = Wire(Vec(p(StoreBufferSize), new StoreBufferEntry))

  for (i <- 0 until p(StoreBufferSize)) {
    val drainedThis = drainRespFire && head === i.U
    val writeHit    = Wire(Vec(numStorePorts, Bool()))
    val commitHit   = Wire(Vec(p(IssueWidth), Bool()))
    val allocHit    = Wire(Vec(p(IssueWidth), Bool()))

    for (s <- 0 until numStorePorts)
      writeHit(s) := io.fu_pool.write(s).valid &&
        io.fu_pool.write(s).bits.sq_idx === i.U &&
        entries(i).valid &&
        entries(i).rob_tag === io.fu_pool.write(s).bits.rob_tag &&
        !drainedThis

    for (c <- 0 until p(IssueWidth))
      commitHit(c) := io.rob.commit(c).valid &&
        io.rob.commit(c).bits.is_store &&
        io.rob.commit(c).bits.sq_idx === i.U &&
        entries(i).valid &&
        !drainedThis

    for (a <- 0 until p(IssueWidth))
      allocHit(a) := allocValid(a) && sqIdxForLane(a) === i.U

    val anyWrite       = writeHit.asUInt.orR
    val anyCommit      = commitHit.asUInt.orR
    val anyAlloc       = allocHit.asUInt.orR
    val writeAddr      = Mux1H(
      (0 until numStorePorts).map(s => writeHit(s) -> io.fu_pool.write(s).bits.addr)
    )
    val writeData      = Mux1H(
      (0 until numStorePorts).map(s => writeHit(s) -> io.fu_pool.write(s).bits.data)
    )
    val writeMask      = Mux1H(
      (0 until numStorePorts).map(s => writeHit(s) -> io.fu_pool.write(s).bits.mask)
    )
    val writeCacheable = Mux1H(
      (0 until numStorePorts).map(s => writeHit(s) -> io.fu_pool.write(s).bits.cacheable)
    )
    val allocSeq       = Mux1H(
      (0 until p(IssueWidth)).map(a => allocHit(a) -> sqSeqForLane(a))
    )
    val allocRobTag    = Mux1H(
      (0 until p(IssueWidth)).map(a => allocHit(a) -> io.dispatch.lanes(a).rob_tag)
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
