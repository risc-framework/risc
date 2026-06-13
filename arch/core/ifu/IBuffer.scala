package arch.core.ifu

import arch.configs._
import vutils.graph.Node
import chisel3._
import chisel3.util.{ PopCount, isPow2, log2Ceil }

class IBuffer(implicit p: Parameters) extends Node[Parameters]("ibuffer") {
  val enqValid = inVecWith[Bool](p => p(IssueWidth))(_ => Bool())

  val enqBits = inVecWith[IBufferEntry](p => p(IssueWidth)) { p =>
    new IBufferEntry()(p)
  }

  val deq = outDVecWith[IBufferEntry](p => p(IssueWidth)) { p =>
    new IBufferEntry()(p)
  }

  val flush  = in[IBufferFlush]
  val status = out[IBufferStatus]

  require(isPow2(p(IBufferSize)), "IBufferSize must be a power of 2")
  require(p(IBufferSize) >= p(IssueWidth), "IBufferSize must be >= IssueWidth")

  private val idxW   = log2Ceil(p(IBufferSize))
  private val countW = log2Ceil(p(IBufferSize) + 1)
  private val mask   = (p(IBufferSize) - 1).U(idxW.W)

  private val buffer = Reg(Vec(p(IBufferSize), new IBufferEntry))
  private val count  = RegInit(0.U(countW.W))
  private val head   = RegInit(0.U(idxW.W))
  private val tail   = RegInit(0.U(idxW.W))

  private val enqValids = Seq.tabulate(p(IssueWidth))(w => enqValid.in.lanes(w))
  private val enqCount  = PopCount(enqValids)

  private val enqOffsets = Wire(Vec(p(IssueWidth), UInt(idxW.W)))
  enqOffsets(0) := 0.U

  for (w <- 1 until p(IssueWidth))
    enqOffsets(w) := (enqOffsets(w - 1) + enqValids(w - 1).asUInt)(idxW - 1, 0)

  private val enqReady = (p(IBufferSize).U - count) >= p(IssueWidth).U
  private val doEnq    = enqReady && enqValids.reduce(_ || _)

  when(doEnq) {
    for (w <- 0 until p(IssueWidth))
      when(enqValid.in.lanes(w)) {
        val idx = ((tail + enqOffsets(w)) & mask)(idxW - 1, 0)
        buffer(idx) := enqBits.in.lanes(w)
      }
  }

  private val deqFires = Seq.tabulate(p(IssueWidth))(w => deq.out.lanes(w).fire)
  private val deqCount = PopCount(deqFires)

  for (w <- 0 until p(IssueWidth)) {
    val idx = if (w == 0) head else ((head + w.U) & mask)(idxW - 1, 0)

    deq.out.lanes(w).valid := count > w.U
    deq.out.lanes(w).bits  := buffer(idx)
  }

  private val actualEnqCount = Mux(doEnq, enqCount, 0.U)

  head  := ((head + deqCount) & mask)(idxW - 1, 0)
  tail  := ((tail + actualEnqCount) & mask)(idxW - 1, 0)
  count := count + actualEnqCount - deqCount

  when(flush.in.flush) {
    count := 0.U
    head  := 0.U
    tail  := 0.U
  }

  status.out.enq_ready := enqReady
  status.out.empty     := count === 0.U
  status.out.full      := count === p(IBufferSize).U
  status.out.count     := count
}
