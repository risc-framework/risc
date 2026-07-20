package arch.core.sb

import arch.configs._
import arch.core.memarb.{ MemoryArbiterCacheReq, MemoryArbiterCacheResp }
import arch.core.rob.RobSbCommit
import vcache.CacheCommand
import vutils.graph.Node
import chisel3._
import chisel3.util.{ Cat, Mux1H, PopCount, Queue, log2Ceil }

private class StoreDrainReq(implicit p: Parameters) extends Bundle {
  val cacheable = Bool()
  val seq       = UInt(p(StoreSeqWidth).W)
  val req       = new MemoryArbiterCacheReq
}

class StoreBuffer(implicit p: Parameters) extends Node[Parameters]("store_buffer") {
  val flush = in[Bool]

  val allocStatus = out[StoreBufferAllocStatus]
  val robAlloc    = inVVec[StoreBufferAllocReq](p => p(IssueWidth))
  val robCommit   = inVVec[RobSbCommit](p => p(CommitWidth))

  val fwdReq  = inDVec[StoreForwardReq](p => p(NumLDs))
  val fwdResp = outDVec[StoreForwardResp](p => p(NumLDs))

  val status = out[StoreBufferStatus]
  val debug  = out[StoreBufferDebugInfo]

  val storeWrite     = inDVec[StoreWriteBundle](p => p(NumSTs))
  val dispatchAddr   = inVVec[StoreAddressBundle](p => p(IssueWidth))
  val schedulerAddr  = inVVec[StoreAddressBundle](p => p(NumSTs))

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

  private def balancedOr(terms: Seq[UInt]): UInt = {
    require(terms.nonEmpty)

    if (terms.size == 1) {
      terms.head
    } else {
      val split = (terms.size + 1) / 2
      balancedOr(terms.take(split)) | balancedOr(terms.drop(split))
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
  private val maxCacheableDrains = 2
  private val cacheableOutstanding = RegInit(0.U(log2Ceil(maxCacheableDrains + 1).W))
  private val mmioOutstanding = RegInit(false.B)
  // The registered entry accepts a drain independently of MemoryArbiter
  // backpressure. This keeps arbiter ready/flush feedback out of both the
  // drain state and the queue write-enable path.
  private val drainReqQ = Module(new Queue(new StoreDrainReq, 1, pipe = false, flow = false))

  private val freeCount = p(StoreBufferSize).U(cntW.W) - count

  allocStatus.out.free_count := freeCount
  allocStatus.out.tail       := tail
  allocStatus.out.tail_seq   := tailSeq

  status.out.oldest_valid := drainReqQ.io.deq.valid || count =/= 0.U
  status.out.oldest_seq := Mux(
    drainReqQ.io.deq.valid,
    drainReqQ.io.deq.bits.seq,
    entries(head).seq
  )
  status.out.unknown_addr := VecInit(entries.map(e => e.valid && !e.addrValid)).asUInt.orR
  status.out.address_signature := balancedOr(
    entries.map(e =>
      Mux(
        e.valid && e.addrValid,
        StoreBufferAddressSignature.oneHot(e.addr),
        0.U(StoreBufferAddressSignature.Width.W)
      )
    ) :+ Mux(
      drainReqQ.io.deq.valid,
      StoreBufferAddressSignature.oneHot(drainReqQ.io.deq.bits.req.addr),
      0.U(StoreBufferAddressSignature.Width.W)
    )
  )

  // St suppresses write valid during flush. Keeping ready unconditional avoids
  // putting the global recovery net on Store completion valid.
  for (s <- 0 until numStorePorts)
    storeWrite.in.lanes(s).ready := true.B

  private val sqIdxForLane = Wire(Vec(p(IssueWidth), UInt(idxW.W)))
  private val allocValid   = Wire(Vec(p(IssueWidth), Bool()))

  for (w <- 0 until p(IssueWidth)) {
    sqIdxForLane(w) := robAlloc.in.lanes(w).bits.sq_idx
    allocValid(w)   := robAlloc.in.lanes(w).valid && !flush.in
  }

  for (q <- 0 until numLoadPorts) {
    val fwdRespValid = RegInit(false.B)
    val fwdRespBits  = Reg(new StoreForwardResp)

    fwdReq.in.lanes(q).ready   := (!fwdRespValid || fwdResp.out.lanes(q).ready) && !flush.in
    // Both forwarding consumers discard their state on flush. Keep valid
    // independent of the flush broadcast so it does not feed their result
    // and wakeup datapaths; ready still prevents consuming the response.
    fwdResp.out.lanes(q).valid := fwdRespValid
    fwdResp.out.lanes(q).bits  := fwdRespBits

    val req        = fwdReq.in.lanes(q).bits
    val reqFire    = fwdReq.in.lanes(q).fire
    val entryData  = Wire(Vec(p(StoreBufferSize), UInt(p(XLen).W)))
    val byteHitVec = Wire(Vec(p(StoreBufferSize), Vec(p(BytesPerWord), Bool())))
    val olderVec   = Wire(Vec(p(StoreBufferSize), Bool()))
    val blockVec   = Wire(Vec(p(StoreBufferSize), Bool()))
    val drainOlder = drainReqQ.io.deq.valid &&
      StoreBufferSequence.isOlder(drainReqQ.io.deq.bits.seq, req.sq_seq)
    val drainSameLine = drainOlder && equalByChunks(
      drainReqQ.io.deq.bits.req.addr(p(XLen) - 1, log2Ceil(p(BytesPerWord))),
      req.addr(p(XLen) - 1, log2Ceil(p(BytesPerWord)))
    )

    for (logical <- 0 until p(StoreBufferSize)) {
      val idx         = wrapAdd(head, logical.U)
      val e           = entries(idx)
      val inRange     = logical.U < count
      val olderLive   = inRange && e.valid && StoreBufferSequence.isOlder(e.seq, req.sq_seq)
      val liveUnknown = olderLive && !e.addrValid
      val addressKnown = olderLive && e.addrValid
      val lineLo      = log2Ceil(p(BytesPerWord))
      val sameLine    = addressKnown && equalByChunks(
        e.addr(p(XLen) - 1, lineLo),
        req.addr(p(XLen) - 1, lineLo)
      )
      val forwardable = sameLine && e.fwdValid

      olderVec(logical) := olderLive
      blockVec(logical) := liveUnknown || (sameLine && !e.fwdValid)
      entryData(logical) := e.data

      for (b <- 0 until p(BytesPerWord))
        byteHitVec(logical)(b) := forwardable && e.mask(b) && req.mask(b)
    }

    // A forwarding request comes from a registered RS entry, so stores allocated
    // in this cycle are necessarily younger than the requesting load.
    val finalMaskVec = Wire(Vec(p(BytesPerWord), Bool()))
    val finalDataVec = Wire(Vec(p(BytesPerWord), UInt(8.W)))

    for (b <- 0 until p(BytesPerWord)) {
      val (hit, data) = mergeForwardBytes(
        Seq(
          (drainSameLine && drainReqQ.io.deq.bits.req.strb(b) && req.mask(b)) ->
            drainReqQ.io.deq.bits.req.data(8 * b + 7, 8 * b)
        ) ++ (0 until p(StoreBufferSize)).map(i =>
          byteHitVec(i)(b) -> entryData(i)(8 * b + 7, 8 * b)
        )
      )

      finalMaskVec(b) := hit
      finalDataVec(b) := data
    }

    val hitMask        = finalMaskVec.asUInt
    val reqMask        = req.mask
    val reqMaskNonZero = reqMask.orR
    val nextResp       = Wire(new StoreForwardResp)

    nextResp.block     := blockVec.asUInt.orR
    nextResp.has_older := drainOlder || olderVec.asUInt.orR
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
  // Count the registered request as in flight before it reaches the shared
  // arbiter. This keeps the two-request limit independent of downstream
  // ready while still allowing the head entry to retire once the queue owns
  // a complete copy of the request.
  private val cacheableDrainsInFlight =
    cacheableOutstanding + drainReqQ.io.deq.valid.asUInt
  private val cacheableDrainRoom = cacheableDrainsInFlight < maxCacheableDrains.U
  private val canDrainCacheable  =
    headEntry.cacheable && cacheableDrainRoom && !mmioOutstanding
  private val canDrainMmio =
    !headEntry.cacheable && cacheableOutstanding === 0.U && !mmioOutstanding
  private val canDrain =
    headEntry.valid && headEntry.committed && headEntry.fwdValid &&
      (canDrainCacheable || canDrainMmio)

  drainReqQ.io.enq.valid          := canDrain
  drainReqQ.io.enq.bits.cacheable := headEntry.cacheable
  drainReqQ.io.enq.bits.seq       := headEntry.seq
  drainReqQ.io.enq.bits.req.cmd   := CacheCommand.Write
  drainReqQ.io.enq.bits.req.addr  := headEntry.addr
  drainReqQ.io.enq.bits.req.data  := headEntry.data
  drainReqQ.io.enq.bits.req.strb  := headEntry.mask

  memReq.out.valid := drainReqQ.io.deq.valid && drainReqQ.io.deq.bits.cacheable
  memReq.out.bits  := drainReqQ.io.deq.bits.req

  mmioReq.out.valid := drainReqQ.io.deq.valid && !drainReqQ.io.deq.bits.cacheable
  mmioReq.out.bits  := drainReqQ.io.deq.bits.req

  drainReqQ.io.deq.ready := Mux(
    drainReqQ.io.deq.bits.cacheable,
    memReq.out.ready,
    mmioReq.out.ready
  )

  memResp.in.ready  := cacheableOutstanding =/= 0.U
  mmioResp.in.ready := mmioOutstanding

  private val drainReqAccept     = drainReqQ.io.enq.fire
  private val cacheableReqAccept = drainReqAccept && headEntry.cacheable
  private val cacheableReqIssue  = drainReqQ.io.deq.fire && drainReqQ.io.deq.bits.cacheable
  private val mmioReqIssue       = drainReqQ.io.deq.fire && !drainReqQ.io.deq.bits.cacheable
  private val cacheableRespFire  = memResp.in.fire
  private val mmioRespFire       = mmioResp.in.fire
  private val headRetireFire     = cacheableReqAccept || mmioRespFire

  debug.out.busy := count =/= 0.U || cacheableOutstanding =/= 0.U ||
    mmioOutstanding || drainReqQ.io.deq.valid
  debug.out.wait_drain := cacheableOutstanding =/= 0.U || mmioOutstanding ||
    drainReqQ.io.deq.valid || (canDrain && !drainReqAccept)

  private val allocCount       = PopCount(allocValid)
  private val commitStoreCount = PopCount(
    Seq.tabulate(p(CommitWidth))(c =>
      robCommit.in.lanes(c).valid && robCommit.in.lanes(c).bits.is_store
    )
  )
  private val afterDrainHead     = Mux(headRetireFire, wrapAdd(head, 1.U), head)
  private val normalTail         = wrapAdd(tail, allocCount)
  private val normalCountWide    = count +& allocCount - headRetireFire.asUInt
  private val normalCount        = normalCountWide(cntW - 1, 0)
  private val committedCountWide = committedCount +& commitStoreCount - headRetireFire.asUInt
  private val nextCommittedCount = committedCountWide(cntW - 1, 0)
  private val flushTail           = wrapAdd(afterDrainHead, nextCommittedCount)
  private val normalSeq           = tailSeq + allocCount

  private val afterOpsEntries = Wire(Vec(p(StoreBufferSize), new StoreBufferEntry))
  private val seqPreloadIdx = VecInit(
    (0 until p(IssueWidth)).map(rank => wrapAdd(tail, rank.U))
  )
  private val seqPreloadValid = VecInit(
    (0 until p(IssueWidth)).map(rank => freeCount > rank.U)
  )
  private val seqPreloadData = VecInit(
    (0 until p(IssueWidth)).map(rank => tailSeq + rank.U)
  )

  for (i <- 0 until p(StoreBufferSize)) {
    val drainedThis = headRetireFire && head === i.U
    val writeHit    = Wire(Vec(numStorePorts, Bool()))
    val dispatchAddrHit = Wire(Vec(p(IssueWidth), Bool()))
    val schedulerAddrHit = Wire(Vec(p(NumSTs), Bool()))
    val commitHit   = Wire(Vec(p(CommitWidth), Bool()))
    val allocHit    = Wire(Vec(p(IssueWidth), Bool()))

    for (s <- 0 until numStorePorts)
      writeHit(s) := storeWrite.in
        .lanes(s)
        .fire && storeWrite.in.lanes(s).bits.sq_idx === i.U && entries(i).valid

    for (a <- 0 until p(IssueWidth))
      dispatchAddrHit(a) := dispatchAddr.in.lanes(a).valid &&
        dispatchAddr.in.lanes(a).bits.sq_idx === i.U

    for (a <- 0 until p(NumSTs))
      schedulerAddrHit(a) := schedulerAddr.in.lanes(a).valid &&
        schedulerAddr.in.lanes(a).bits.sq_idx === i.U

    for (c <- 0 until p(CommitWidth))
      commitHit(c) := robCommit.in.lanes(c).valid && robCommit.in
        .lanes(c)
        .bits
        .is_store && robCommit.in.lanes(c).bits.sq_idx === i.U && entries(i).valid

    for (a <- 0 until p(IssueWidth))
      allocHit(a) := allocValid(a) && sqIdxForLane(a) === i.U

    val anyWrite       = writeHit.asUInt.orR
    val anyDispatchAddr = dispatchAddrHit.asUInt.orR
    val anySchedulerAddr = schedulerAddrHit.asUInt.orR
    val anyAddr        = anyDispatchAddr || anySchedulerAddr
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
    val dispatchEarlyAddr = Mux1H(
      (0 until p(IssueWidth)).map(a =>
        dispatchAddrHit(a) -> dispatchAddr.in.lanes(a).bits.addr
      )
    )
    val schedulerEarlyAddr = Mux1H(
      (0 until p(NumSTs)).map(a =>
        schedulerAddrHit(a) -> schedulerAddr.in.lanes(a).bits.addr
      )
    )
    val earlyAddr      = Mux(anyDispatchAddr, dispatchEarlyAddr, schedulerEarlyAddr)
    val e              = Wire(new StoreBufferEntry)

    e := entries(i)

    // The registered tail and free count prove that each preload target is a
    // free slot. Prepare only those slots rather than driving every invalid
    // entry on every cycle. For every legal allocation, the preloaded sequence
    // is identical to the ROB ticket (tailSeq plus the store-lane prefix), and
    // its enable stays independent of the current dispatch-ready chain.
    for (rank <- 0 until p(IssueWidth)) {
      when(
        seqPreloadValid(rank) && seqPreloadIdx(rank) === i.U
      ) {
        e.committed := false.B
        e.addrValid := false.B
        e.fwdValid  := false.B
        e.seq       := seqPreloadData(rank)
      }
    }

    when(drainedThis) {
      // Once valid is clear, the payload is unreachable and can remain stale.
      // Avoid driving every payload register with the drain condition.
      e.valid := false.B
    }.elsewhen(anyAlloc) {
      e.valid := true.B
    }

    // Writes and commits only hit pre-existing valid entries, whereas an
    // allocation always targets a free slot.  Keeping these updates
    // independent prevents allocation control from being replicated across
    // their register inputs.
    when(anyWrite) {
      e.addrValid := true.B
      e.fwdValid  := true.B
      e.data      := writeData
      e.mask      := writeMask
      e.cacheable := writeCacheable
    }

    when(anyCommit) {
      e.committed := true.B
    }

    // An early address may arrive with allocation, so it is intentionally
    // outside the allocation-priority block. A draining entry becomes invalid
    // before afterOpsEntries is observable, so adding drain to this enable only
    // pulls the long retirement cone into every address-valid register.
    when(anyAddr) {
      e.addrValid := true.B
    }

    // Address updates do not need the allocation-priority selector that
    // governs the rest of the entry payload. Allocation and execution write
    // are mutually exclusive for one live SQ slot; an early address may
    // accompany allocation, while a later execution write is authoritative.
    // Keeping this field separate prevents current dispatch/alloc control from
    // feeding every address-register clock enable.
    e.addr := entries(i).addr
    when(anyAddr) {
      e.addr := earlyAddr
    }
    when(anyWrite) {
      e.addr := writeAddr
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

  cacheableOutstanding :=
    cacheableOutstanding + cacheableReqIssue.asUInt - cacheableRespFire.asUInt

  when(mmioReqIssue) {
    mmioOutstanding := true.B
  }.elsewhen(mmioRespFire) {
    mmioOutstanding := false.B
  }
}
