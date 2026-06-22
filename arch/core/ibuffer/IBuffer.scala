package arch.core.ibuffer

import arch.configs._
import vutils.graph.Node
import chisel3._
import chisel3.util.{ PopCount, isPow2, log2Ceil }

class IBuffer(implicit p: Parameters) extends Node[Parameters]("ibuffer") {
  val enq = inDVec[IBufferEntry](p => p(IssueWidth))
  val deq = outDVec[IBufferEntry](p => p(IssueWidth))

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

  // Enqueue logic
  private val enqFires = Seq.tabulate(p(IssueWidth))(w => enq.in.lanes(w).fire)
  private val enqCount = PopCount(enqFires)

  private val enqOffsets = Wire(Vec(p(IssueWidth), UInt(idxW.W)))
  enqOffsets(0) := 0.U

  for (w <- 1 until p(IssueWidth))
    enqOffsets(w) := (enqOffsets(w - 1) + enqFires(w - 1).asUInt)(idxW - 1, 0)

  for (w <- 0 until p(IssueWidth)) {
    enq.in.lanes(w).ready := (p(IBufferSize).U - count) >= p(IssueWidth).U
    when(enqFires(w)) {
      val idx = ((tail + enqOffsets(w)) & mask)(idxW - 1, 0)
      buffer(idx) := enq.in.lanes(w).bits
    }
  }

  // Dequeue logic
  private val deqFires = Seq.tabulate(p(IssueWidth))(w => deq.out.lanes(w).fire)
  private val deqCount = PopCount(deqFires)

  for (w <- 0 until p(IssueWidth)) {
    val idx = if (w == 0) head else ((head + w.U) & mask)(idxW - 1, 0)

    deq.out.lanes(w).valid := count > w.U
    deq.out.lanes(w).bits  := buffer(idx)
  }

  // Update logic
  head  := ((head + deqCount) & mask)(idxW - 1, 0)
  tail  := ((tail + enqCount) & mask)(idxW - 1, 0)
  count := count + enqCount - deqCount

  when(flush.in.flush) {
    count := 0.U
    head  := 0.U
    tail  := 0.U
  }

  status.out.ready := Seq.tabulate(p(IssueWidth))(w => enq.in.lanes(w).ready).reduce(_ || _)
  status.out.empty := count === 0.U
  status.out.full  := count === p(IBufferSize).U
  status.out.count := count
}
