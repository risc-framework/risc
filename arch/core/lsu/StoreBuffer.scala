package arch.core.lsu

import arch.configs._
import vcache._
import chisel3._
import chisel3.util.{ log2Ceil, Decoupled, Valid, Cat, Mux1H, PopCount }

class StoreAllocBundle(implicit p: Parameters) extends Bundle {
  val sq_idx  = UInt(log2Ceil(p(StoreBufferSize)).W)
  val sq_seq  = UInt(64.W)
  val rob_tag = UInt(p(RobTagWidth).W)
}

class StoreWriteBundle(implicit p: Parameters) extends Bundle {
  val sq_idx    = UInt(log2Ceil(p(StoreBufferSize)).W)
  val rob_tag   = UInt(p(RobTagWidth).W)
  val addr      = UInt(p(XLen).W)
  val data      = UInt(p(XLen).W)
  val mask      = UInt(p(BytesPerWord).W)
  val cacheable = Bool()
}

class StoreForwardReq(implicit p: Parameters) extends Bundle {
  val valid  = Bool()
  val sq_seq = UInt(64.W)
  val addr   = UInt(p(XLen).W)
  val mask   = UInt(p(BytesPerWord).W)
}

class StoreForwardResp(implicit p: Parameters) extends Bundle {
  val block    = Bool()
  val hasOlder = Bool()
  val fwdValid = Bool()
  val fwdFull  = Bool()
  val fwdData  = UInt(p(XLen).W)
  val fwdMask  = UInt(p(BytesPerWord).W)
}

class StoreForwardPort(implicit p: Parameters) extends Bundle {
  val req  = Flipped(Decoupled(new StoreForwardReq))
  val resp = Decoupled(new StoreForwardResp)
}

class StoreBufferEntry(implicit p: Parameters) extends Bundle {
  val valid     = Bool()
  val committed = Bool()
  val addrValid = Bool()
  val fwdValid  = Bool()
  val seq       = UInt(64.W)
  val rob_tag   = UInt(p(RobTagWidth).W)
  val addr      = UInt(p(XLen).W)
  val data      = UInt(p(XLen).W)
  val mask      = UInt(p(BytesPerWord).W)
  val cacheable = Bool()
}

class StoreBuffer(numLoadPorts: Int, numStorePorts: Int)(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_store_buffer"

  private val IdxW = log2Ceil(p(StoreBufferSize))
  private val CntW = log2Ceil(p(StoreBufferSize) + 1)

  val io = IO(new Bundle {
    val alloc       = Flipped(Vec(p(IssueWidth), Valid(new StoreAllocBundle)))
    val commit      = Flipped(Vec(p(IssueWidth), Valid(UInt(IdxW.W))))
    val write       = Flipped(Vec(numStorePorts, Valid(new StoreWriteBundle)))
    val fwd         = Vec(numLoadPorts, new StoreForwardPort)
    val tail        = Output(UInt(IdxW.W))
    val tailSeq     = Output(UInt(64.W))
    val freeCount   = Output(UInt(CntW.W))
    val empty       = Output(Bool())
    val busy        = Output(Bool())
    val oldestValid = Output(Bool())
    val oldestSeq   = Output(UInt(64.W))
    val mem         = new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams))
    val mmio        = new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams))
    val flush       = Input(Bool())
  })

  private def wrapAdd(x: UInt, y: UInt): UInt = {
    val sum = x +& y
    Mux(sum >= p(StoreBufferSize).U, sum - p(StoreBufferSize).U, sum)(IdxW - 1, 0)
  }

  private def zeroEntry: StoreBufferEntry = 0.U.asTypeOf(new StoreBufferEntry)

  val entries          = RegInit(VecInit(Seq.fill(p(StoreBufferSize))(zeroEntry)))
  val head             = RegInit(0.U(IdxW.W))
  val tail             = RegInit(0.U(IdxW.W))
  val count            = RegInit(0.U(CntW.W))
  val tailSeq          = RegInit(0.U(64.W))
  val drainOutstanding = RegInit(false.B)
  val drainIsCacheable = RegInit(false.B)

  io.tail        := tail
  io.tailSeq     := tailSeq
  io.freeCount   := p(StoreBufferSize).U(CntW.W) - count
  io.empty       := count === 0.U && !drainOutstanding
  io.busy        := count =/= 0.U || drainOutstanding
  io.oldestValid := count =/= 0.U
  io.oldestSeq   := entries(head).seq

  for (q <- 0 until numLoadPorts) {
    val fwdRespValid = RegInit(false.B)
    val fwdRespBits  = RegInit(0.U.asTypeOf(new StoreForwardResp))

    io.fwd(q).req.ready  := (!fwdRespValid || io.fwd(q).resp.ready) && !io.flush
    io.fwd(q).resp.valid := fwdRespValid && !io.flush
    io.fwd(q).resp.bits  := fwdRespBits

    val req       = io.fwd(q).req.bits
    val reqFire   = io.fwd(q).req.fire
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

    val finalMaskVec  = maskStage(p(StoreBufferSize))
    val finalDataVec  = dataStage(p(StoreBufferSize))
    val finalMaskUInt = finalMaskVec.asUInt

    val nextResp = Wire(new StoreForwardResp)
    nextResp.block    := blockVec.asUInt.orR
    nextResp.hasOlder := olderVec.asUInt.orR
    nextResp.fwdValid := reqFire && req.valid && (finalMaskUInt & req.mask).orR
    nextResp.fwdFull  := reqFire && req.valid && ((finalMaskUInt & req.mask) === req.mask)
    nextResp.fwdData  := Cat((p(BytesPerWord) - 1 to 0 by -1).map(i => finalDataVec(i)))
    nextResp.fwdMask  := finalMaskUInt

    when(io.flush) {
      fwdRespValid := false.B
      fwdRespBits  := 0.U.asTypeOf(new StoreForwardResp)
    }.otherwise {
      when(reqFire) {
        fwdRespValid := true.B
        fwdRespBits  := nextResp
      }.elsewhen(io.fwd(q).resp.fire) {
        fwdRespValid := false.B
      }
    }
  }

  val headEntry = entries(head)
  val canDrain  = headEntry.valid && headEntry.committed && headEntry.addrValid && !drainOutstanding

  io.mem.req.valid       := canDrain && headEntry.cacheable
  io.mem.req.bits.cmd    := CacheCommand.Write
  io.mem.req.bits.addr   := headEntry.addr
  io.mem.req.bits.data   := headEntry.data
  io.mem.req.bits.strb   := headEntry.mask
  io.mem.req.bits.source := 0.U

  io.mmio.req.valid       := canDrain && !headEntry.cacheable
  io.mmio.req.bits.cmd    := CacheCommand.Write
  io.mmio.req.bits.addr   := headEntry.addr
  io.mmio.req.bits.data   := headEntry.data
  io.mmio.req.bits.strb   := headEntry.mask
  io.mmio.req.bits.source := 0.U

  io.mem.resp.ready  := drainOutstanding && drainIsCacheable
  io.mmio.resp.ready := drainOutstanding && !drainIsCacheable

  val drainReqFire  = io.mem.req.fire || io.mmio.req.fire
  val drainRespFire = io.mem.resp.fire || io.mmio.resp.fire

  val allocValid = Wire(Vec(p(IssueWidth), Bool()))
  for (a <- 0 until p(IssueWidth)) allocValid(a) := io.alloc(a).valid && !io.flush

  val allocCount      = PopCount(allocValid)
  val afterDrainHead  = Mux(drainRespFire, wrapAdd(head, 1.U), head)
  val normalTail      = wrapAdd(tail, allocCount)
  val normalCountWide = count +& allocCount - drainRespFire.asUInt
  val normalCount     = normalCountWide(CntW - 1, 0)
  val normalSeq       = tailSeq + allocCount

  val afterOpsEntries = Wire(Vec(p(StoreBufferSize), new StoreBufferEntry))

  for (i <- 0 until p(StoreBufferSize)) {
    val drainedThis = drainRespFire && head === i.U
    val writeHit    = Wire(Vec(numStorePorts, Bool()))
    val commitHit   = Wire(Vec(p(IssueWidth), Bool()))
    val allocHit    = Wire(Vec(p(IssueWidth), Bool()))

    for (s <- 0 until numStorePorts)
      writeHit(s) := io.write(s).valid && io.write(s).bits.sq_idx === i.U && entries(i).valid && entries(i).rob_tag === io.write(s).bits.rob_tag && !drainedThis

    for (c <- 0 until p(IssueWidth))
      commitHit(c) := io.commit(c).valid && io.commit(c).bits === i.U && entries(i).valid && !drainedThis

    for (a <- 0 until p(IssueWidth))
      allocHit(a) := allocValid(a) && io.alloc(a).bits.sq_idx === i.U

    val anyWrite       = writeHit.asUInt.orR
    val anyCommit      = commitHit.asUInt.orR
    val anyAlloc       = allocHit.asUInt.orR
    val writeAddr      = Mux1H((0 until numStorePorts).map(s => writeHit(s) -> io.write(s).bits.addr))
    val writeData      = Mux1H((0 until numStorePorts).map(s => writeHit(s) -> io.write(s).bits.data))
    val writeMask      = Mux1H((0 until numStorePorts).map(s => writeHit(s) -> io.write(s).bits.mask))
    val writeCacheable = Mux1H((0 until numStorePorts).map(s => writeHit(s) -> io.write(s).bits.cacheable))
    val allocSeq       = Mux1H((0 until p(IssueWidth)).map(a => allocHit(a) -> io.alloc(a).bits.sq_seq))
    val allocRobTag    = Mux1H((0 until p(IssueWidth)).map(a => allocHit(a) -> io.alloc(a).bits.rob_tag))

    val e = Wire(new StoreBufferEntry)
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

  val keepPrefix  = Wire(Vec(p(StoreBufferSize), Bool()))
  val prefixAlive = Wire(Vec(p(StoreBufferSize) + 1, Bool()))

  prefixAlive(0) := true.B

  for (logical <- 0 until p(StoreBufferSize)) {
    val idx     = wrapAdd(afterDrainHead, logical.U)
    val e       = afterOpsEntries(idx)
    val inRange = logical.U < normalCount

    keepPrefix(logical)      := prefixAlive(logical) && inRange && e.valid && e.committed
    prefixAlive(logical + 1) := keepPrefix(logical)
  }

  val flushCount = PopCount(keepPrefix)
  val flushTail  = wrapAdd(afterDrainHead, flushCount)

  val keepPhysical = Wire(Vec(p(StoreBufferSize), Bool()))

  for (i <- 0 until p(StoreBufferSize)) {
    val keepHits = Wire(Vec(p(StoreBufferSize), Bool()))

    for (logical <- 0 until p(StoreBufferSize))
      keepHits(logical) := keepPrefix(logical) && wrapAdd(afterDrainHead, logical.U) === i.U

    keepPhysical(i) := keepHits.asUInt.orR
  }

  for (i <- 0 until p(StoreBufferSize))
    when(io.flush && !keepPhysical(i)) {
      entries(i) := zeroEntry
    }.otherwise {
      entries(i) := afterOpsEntries(i)
    }

  head    := afterDrainHead
  tail    := Mux(io.flush, flushTail, normalTail)
  count   := Mux(io.flush, flushCount, normalCount)
  tailSeq := normalSeq

  when(drainReqFire) {
    drainOutstanding := true.B
    drainIsCacheable := headEntry.cacheable
  }

  when(drainRespFire) {
    drainOutstanding := false.B
  }
}
