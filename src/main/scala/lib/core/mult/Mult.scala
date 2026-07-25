package arch.core.mult

import arch.configs._
import arch.core.fupool.{ FuResp, FuReq }
import vutils.graph.{ Node, NodeConfig, NodeSelector }
import vutils.math.mul.IntegerMultiplier
import chisel3._
import chisel3.util.Queue

class Mult(implicit p: Parameters) extends Node[Parameters]("mult") {
  override protected def cfg: NodeConfig = NodeConfig(
    selector = NodeSelector(
      MultDims.ISA -> p(ISA).name
    )
  )

  val fuReq  = inD[FuReq]
  val fuResp = outD[FuResp]
  val flush  = in[Bool]

  private val isaImpl    = MultIsaFactory.select(cfg)
  private val multiplier = Module(new IntegerMultiplier(p(XLen), p(MultPipelineStages)))
  private val uopQ       = Module(new Queue(new FuReq, p(MultPipelineStages) + 2, hasFlush = true))

  private val ctrl = isaImpl.decode(fuReq.in.bits.uop)

  // Scheduler, ROB, and the uop metadata queue are all flushed on the raw
  // globalFlush edge.  Clear the multiplier's private pipeline one cycle
  // later so raw globalFlush does not feed its output-valid path; requests and
  // dequeues remain blocked on the original flush edge below.
  private val multiplierKill = RegNext(flush.in, false.B)

  fuReq.in.ready := !flush.in && multiplier.io.in.ready && uopQ.io.enq.ready

  multiplier.io.kill                 := multiplierKill
  multiplier.io.in.valid             := !flush.in && fuReq.in.valid && uopQ.io.enq.ready
  multiplier.io.in.bits.multiplicand := fuReq.in.bits.rs1_data
  multiplier.io.in.bits.multiplier   := fuReq.in.bits.rs2_data
  multiplier.io.in.bits.aSigned      := ctrl.a_signed
  multiplier.io.in.bits.bSigned      := ctrl.b_signed
  multiplier.io.in.bits.takeHigh     := ctrl.high

  uopQ.io.flush.get := flush.in
  uopQ.io.enq.valid := !flush.in && fuReq.in.valid && multiplier.io.in.ready
  uopQ.io.enq.bits  := fuReq.in.bits

  multiplier.io.out.ready := !flush.in && uopQ.io.deq.valid && fuResp.out.ready
  uopQ.io.deq.ready       := !flush.in && multiplier.io.out.valid && fuResp.out.ready

  fuResp.out.valid := multiplier.io.out.valid && uopQ.io.deq.valid

  fuResp.out.bits.result  := multiplier.io.out.bits.result
  fuResp.out.bits.rd      := uopQ.io.deq.bits.rd
  fuResp.out.bits.pc      := uopQ.io.deq.bits.pc
  fuResp.out.bits.instr   := uopQ.io.deq.bits.instr
  fuResp.out.bits.rob_tag := uopQ.io.deq.bits.rob_tag
  fuResp.out.bits.trap_req    := false.B
  fuResp.out.bits.trap_kind   := 0.U
  fuResp.out.bits.trap_target := 0.U
}
