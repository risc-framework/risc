package arch.core.sb

import arch.configs._
import arch.core.memarb.{ MemoryArbiterCacheReq, MemoryArbiterCacheResp }
import arch.core.rob.RobSbCommit
import vcache.CacheCommand
import vutils.graph.Node
import chisel3._
import chisel3.util.{ Cat, Mux1H, PopCount, log2Ceil }

class StoreBuffer(implicit p: Parameters) extends Node[Parameters]("store_buffer") {
  val flush = in[Bool]

  val allocStatus = out[StoreBufferAllocStatus]
  val robAlloc    = inVVec[StoreBufferAllocReq](p => p(IssueWidth))
  val robCommit   = inVVec[RobSbCommit](p => p(CommitWidth))

  val fwdReq  = inDVec[StoreForwardReq](p => p(NumLDs))
  val fwdResp = outDVec[StoreForwardResp](p => p(NumLDs))

  val status = out[StoreBufferStatus]
  val debug  = out[StoreBufferDebugInfo]

  val storeWrite = inDVec[StoreWriteBundle](p => p(NumSTs))

  val memReq   = outD[MemoryArbiterCacheReq]
  val memResp  = inD[MemoryArbiterCacheResp]
  val mmioReq  = outD[MemoryArbiterCacheReq]
  val mmioResp = inD[MemoryArbiterCacheResp]

  private val numLoadPorts  = p(NumLDs)
  private val numStorePorts = p(NumSTs)
  private val idxW          = log2Ceil(p(StoreBufferSize))
  private val cntW          = log2Ceil(p(StoreBufferSize) + 1)

  private def wrapAdd(x: UInt, y: UInt): UInt = {
    val sum = x +& y
    Mux(sum >= p(StoreBufferSize).U, sum - p(StoreBufferSize).U, sum)(idxW - 1, 0)
  }

  private def zeroEntry: StoreBufferEntry = 0.U.asTypeOf(new StoreBufferEntry)

  private def balancedAnd(terms: Seq[Bool]): Bool = {
    require(terms.nonEmpty)

    if (terms.size == 1) {
      terms.head
    } else {
      val split = (terms.size + 1) / 2
      balancedAnd(terms.take(split)) && balancedAnd(terms.drop(split))
    }
  }

  private def equalByChunks(lhs: UInt, rhs: UInt, chunkWidth: Int = 3): Bool = {
    require(lhs.getWidth == rhs.getWidth)
    require(chunkWidth > 0)

    balancedAnd((0 until lhs.getWidth by chunkWidth).map { lo =>
      val hi = math.min(lo + chunkWidth, lhs.getWidth) - 1
      lhs(hi, lo) === rhs(hi, lo)
    })
  }

  private def mergeForwardBytes(candidates: Seq[(Bool, UInt)]): (Bool, UInt) = {
    require(candidates.nonEmpty)

    if (candidates.size == 1) {
      candidates.head
    } else {
      val split                 = (candidates.size + 1) / 2
      val (olderHit, olderData) = mergeForwardBytes(candidates.take(split))
      val (newerHit, newerData) = mergeForwardBytes(candidates.drop(split))

      (olderHit || newerHit, Mux(newerHit, newerData, olderData))
    }
  }

  private val entries          = RegInit(VecInit(Seq.fill(p(StoreBufferSize))(zeroEntry)))
  private val head             = RegInit(0.U(idxW.W))
  private val tail             = RegInit(0.U(idxW.W))
  private val count            = RegInit(0.U(cntW.W))
  private val committedCount   = RegInit(0.U(cntW.W))
  private val tailSeq          = RegInit(0.U(p(StoreSeqWidth).W))
  private val drainOutstanding = RegInit(false.B)
  private val drainIsCacheable = RegInit(false.B)

  private val freeCount = p(StoreBufferSize).U(cntW.W) - count

  allocStatus.out.free_count := freeCount
  allocStatus.out.tail       := tail
  allocStatus.out.tail_seq   := tailSeq

  status.out.oldest_valid := count =/= 0.U
  status.out.oldest_seq   := entries(head).seq

  for (s <- 0 until numStorePorts)
    storeWrite.in.lanes(s).ready := !flush.in

  private val sqIdxForLane = Wire(Vec(p(IssueWidth), UInt(idxW.W)))
  private val sqSeqForLane = Wire(Vec(p(IssueWidth), UInt(p(StoreSeqWidth).W)))
  private val allocValid   = Wire(Vec(p(IssueWidth), Bool()))

  for (w <- 0 until p(IssueWidth)) {
    sqIdxForLane(w) := robAlloc.in.lanes(w).bits.sq_idx
    sqSeqForLane(w) := robAlloc.in.lanes(w).bits.sq_seq
    allocValid(w)   := robAlloc.in.lanes(w).valid && !flush.in
  }

  for (q <- 0 until numLoadPorts) {
    val fwdRespValid = RegInit(false.B)
    val fwdRespBits  = Reg(new StoreForwardResp)

    fwdReq.in.lanes(q).ready   := (!fwdRespValid || fwdResp.out.lanes(q).ready) && !flush.in
    fwdResp.out.lanes(q).valid := fwdRespValid && !flush.in
    fwdResp.out.lanes(q).bits  := fwdRespBits

    val req        = fwdReq.in.lanes(q).bits
    val reqFire    = fwdReq.in.lanes(q).fire
    val entryData  = Wire(Vec(p(StoreBufferSize), UInt(p(XLen).W)))
    val byteHitVec = Wire(Vec(p(StoreBufferSize), Vec(p(BytesPerWord), Bool())))
    val olderVec   = Wire(Vec(p(StoreBufferSize), Bool()))
    val blockVec   = Wire(Vec(p(StoreBufferSize), Bool()))

    for (logical <- 0 until p(StoreBufferSize)) {
      val idx         = wrapAdd(head, logical.U)
      val e           = entries(idx)
      val inRange     = logical.U < count
      val olderLive   = inRange && e.valid && StoreBufferSequence.isOlder(e.seq, req.sq_seq)
      val liveUnknown = olderLive && !e.addrValid
      val forwardable = olderLive && e.addrValid
      val lineLo      = log2Ceil(p(BytesPerWord))
      val sameLine    = forwardable && equalByChunks(
        e.addr(p(XLen) - 1, lineLo),
        req.addr(p(XLen) - 1, lineLo)
      )

      olderVec(logical) := olderLive
      blockVec(logical) := liveUnknown
      entryData(logical) := e.data

      for (b <- 0 until p(BytesPerWord))
        byteHitVec(logical)(b) := sameLine && e.mask(b) && req.mask(b)
    }

    // A forwarding request comes from a registered RS entry, so stores allocated
    // in this cycle are necessarily younger than the requesting load.
    val finalMaskVec = Wire(Vec(p(BytesPerWord), Bool()))
    val finalDataVec = Wire(Vec(p(BytesPerWord), UInt(8.W)))

    for (b <- 0 until p(BytesPerWord)) {
      val (hit, data) = mergeForwardBytes(
        (0 until p(StoreBufferSize)).map(i => byteHitVec(i)(b) -> entryData(i)(8 * b + 7, 8 * b))
      )

      finalMaskVec(b) := hit
      finalDataVec(b) := data
    }

    val hitMask        = finalMaskVec.asUInt
    val reqMask        = req.mask
    val reqMaskNonZero = reqMask.orR
    val nextResp       = Wire(new StoreForwardResp)

    nextResp.block     := blockVec.asUInt.orR
    nextResp.has_older := olderVec.asUInt.orR
    nextResp.valid     := reqMaskNonZero && hitMask.orR
    nextResp.full      := reqMaskNonZero && hitMask === reqMask
    nextResp.data      := Cat((p(BytesPerWord) - 1 to 0 by -1).map(i => finalDataVec(i)))
    nextResp.mask      := hitMask

    when(flush.in) {
      fwdRespValid := false.B
    }.otherwise {
      when(reqFire) {
        fwdRespValid := true.B
        fwdRespBits  := nextResp
      }.elsewhen(fwdResp.out.lanes(q).fire) {
        fwdRespValid := false.B
      }
    }
  }

  private val headEntry = entries(head)
  private val canDrain  =
    headEntry.valid && headEntry.committed && headEntry.addrValid && !drainOutstanding

  memReq.out.valid     := canDrain && headEntry.cacheable
  memReq.out.bits.cmd  := CacheCommand.Write
  memReq.out.bits.addr := headEntry.addr
  memReq.out.bits.data := headEntry.data
  memReq.out.bits.strb := headEntry.mask

  mmioReq.out.valid     := canDrain && !headEntry.cacheable
  mmioReq.out.bits.cmd  := CacheCommand.Write
  mmioReq.out.bits.addr := headEntry.addr
  mmioReq.out.bits.data := headEntry.data
  mmioReq.out.bits.strb := headEntry.mask

  memResp.in.ready  := drainOutstanding && drainIsCacheable
  mmioResp.in.ready := drainOutstanding && !drainIsCacheable

  private val drainReqFire  = memReq.out.fire || mmioReq.out.fire
  private val drainRespFire = memResp.in.fire || mmioResp.in.fire

  debug.out.busy       := count =/= 0.U || drainOutstanding
  debug.out.wait_drain := drainOutstanding || (canDrain && !drainReqFire)

  private val allocCount       = PopCount(allocValid)
  private val commitStoreCount = PopCount(
    Seq.tabulate(p(CommitWidth))(c =>
      robCommit.in.lanes(c).valid && robCommit.in.lanes(c).bits.is_store
    )
  )
  private val afterDrainHead     = Mux(drainRespFire, wrapAdd(head, 1.U), head)
  private val normalTail         = wrapAdd(tail, allocCount)
  private val normalCountWide    = count +& allocCount - drainRespFire.asUInt
  private val normalCount        = normalCountWide(cntW - 1, 0)
  private val committedCountWide = committedCount +& commitStoreCount - drainRespFire.asUInt
  private val nextCommittedCount = committedCountWide(cntW - 1, 0)
  private val flushTail           = wrapAdd(afterDrainHead, nextCommittedCount)
  private val normalSeq           = tailSeq + allocCount

  private val afterOpsEntries = Wire(Vec(p(StoreBufferSize), new StoreBufferEntry))

  for (i <- 0 until p(StoreBufferSize)) {
    val drainedThis = drainRespFire && head === i.U
    val writeHit    = Wire(Vec(numStorePorts, Bool()))
    val commitHit   = Wire(Vec(p(CommitWidth), Bool()))
    val allocHit    = Wire(Vec(p(IssueWidth), Bool()))

    for (s <- 0 until numStorePorts)
      writeHit(s) := storeWrite.in
        .lanes(s)
        .fire && storeWrite.in.lanes(s).bits.sq_idx === i.U && entries(i).valid && !drainedThis

    for (c <- 0 until p(CommitWidth))
      commitHit(c) := robCommit.in.lanes(c).valid && robCommit.in
        .lanes(c)
        .bits
        .is_store && robCommit.in.lanes(c).bits.sq_idx === i.U && entries(i).valid && !drainedThis

    for (a <- 0 until p(IssueWidth))
      allocHit(a) := allocValid(a) && sqIdxForLane(a) === i.U

    val anyWrite       = writeHit.asUInt.orR
    val anyCommit      = commitHit.asUInt.orR
    val anyAlloc       = allocHit.asUInt.orR
    val writeAddr      = Mux1H(
      (0 until numStorePorts).map(s => writeHit(s) -> storeWrite.in.lanes(s).bits.addr)
    )
    val writeData      = Mux1H(
      (0 until numStorePorts).map(s => writeHit(s) -> storeWrite.in.lanes(s).bits.data)
    )
    val writeMask      = Mux1H(
      (0 until numStorePorts).map(s => writeHit(s) -> storeWrite.in.lanes(s).bits.mask)
    )
    val writeCacheable = Mux1H(
      (0 until numStorePorts).map(s => writeHit(s) -> storeWrite.in.lanes(s).bits.cacheable)
    )
    val allocSeq       = Mux1H((0 until p(IssueWidth)).map(a => allocHit(a) -> sqSeqForLane(a)))
    val e              = Wire(new StoreBufferEntry)

    e := entries(i)

    when(drainedThis) {
      // Once valid is clear, the payload is unreachable and can remain stale.
      // Avoid driving every payload register with the drain condition.
      e.valid := false.B
    }.elsewhen(anyAlloc) {
      e.valid     := true.B
      e.committed := false.B
      e.addrValid := false.B
      e.fwdValid  := false.B
      e.seq       := allocSeq
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

  for (i <- 0 until p(StoreBufferSize)) {
    entries(i) := afterOpsEntries(i)

    when(flush.in && !(afterOpsEntries(i).valid && afterOpsEntries(i).committed)) {
      // A flushed speculative entry only needs to become unreachable. Keeping
      // its payload removes globalFlush from every entry payload reset path.
      entries(i).valid := false.B
    }
  }

  head           := afterDrainHead
  tail           := Mux(flush.in, flushTail, normalTail)
  count          := Mux(flush.in, nextCommittedCount, normalCount)
  committedCount := nextCommittedCount
  tailSeq        := normalSeq

  when(drainReqFire) {
    drainOutstanding := true.B
    drainIsCacheable := headEntry.cacheable
  }

  when(drainRespFire) {
    drainOutstanding := false.B
  }
}
