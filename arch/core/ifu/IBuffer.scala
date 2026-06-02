package arch.core.ifu

import arch.configs._
import vutils.graph.{ Node, NodeType }
import chisel3._
import chisel3.util.{ Decoupled, PopCount, isPow2, log2Ceil }

class IBufferIO(implicit p: Parameters) extends Bundle {
  val enq_valid = Input(Vec(p(IssueWidth), Bool()))
  val enq_bits  = Input(Vec(p(IssueWidth), new IBufferEntry))
  val enq_ready = Output(Bool())
  val deq       = Vec(p(IssueWidth), Decoupled(new IBufferEntry))
  val empty     = Output(Bool())
  val full      = Output(Bool())
  val flush     = Input(Bool())
}

class IBuffer(implicit p: Parameters) extends Node(new IBufferIO) {
  override def nodeType: NodeType  = NodeType("ibuffer")
  override def desiredName: String = "ibuffer"

  require(isPow2(p(IBufferSize)), "IBufferSize must be a power of 2")

  private val idxW   = log2Ceil(p(IBufferSize))
  private val countW = log2Ceil(p(IBufferSize) + 1)
  private val mask   = (p(IBufferSize) - 1).U

  private val buffer = Reg(Vec(p(IBufferSize), new IBufferEntry))
  private val count  = RegInit(0.U(countW.W))
  private val head   = RegInit(0.U(idxW.W))
  private val tail   = RegInit(0.U(idxW.W))

  private val enqValids  = io.enq_valid.map(_.asUInt)
  private val enqCount   = PopCount(io.enq_valid)
  private val enqOffsets = Wire(Vec(p(IssueWidth), UInt(idxW.W)))

  enqOffsets(0) := 0.U

  for (w <- 1 until p(IssueWidth))
    enqOffsets(w) := (enqOffsets(w - 1) + enqValids(w - 1))(idxW - 1, 0)

  io.enq_ready := (p(IBufferSize).U - count) >= p(IssueWidth).U

  private val doEnq = io.enq_ready && io.enq_valid.reduce(_ || _)

  when(doEnq) {
    for (w <- 0 until p(IssueWidth))
      when(io.enq_valid(w)) {
        val idx = ((tail + enqOffsets(w)) & mask)(idxW - 1, 0)
        buffer(idx) := io.enq_bits(w)
      }
  }

  private val deqFires = io.deq.map(_.fire)
  private val deqCount = PopCount(deqFires)

  for (w <- 0 until p(IssueWidth)) {
    io.deq(w).valid := count > w.U
    val idx = if (w == 0) head else ((head + w.U) & mask)(idxW - 1, 0)
    io.deq(w).bits := buffer(idx)
  }

  private val actualEnqCount = Mux(doEnq, enqCount, 0.U)

  head  := ((head + deqCount) & mask)(idxW - 1, 0)
  tail  := ((tail + actualEnqCount) & mask)(idxW - 1, 0)
  count := count + actualEnqCount - deqCount

  when(io.flush) {
    count := 0.U
    head  := 0.U
    tail  := 0.U
  }

  io.empty := count === 0.U
  io.full  := count === p(IBufferSize).U
}
