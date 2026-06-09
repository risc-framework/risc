package arch.core.bru

import arch.core.fupool.{ FuFlushReq, FuResp }
import arch.core.fupool.FuReq
import arch.configs._
import vutils.graph.{ Node, NodeConfig, NodeSelector }
import chisel3._

class Bru(implicit p: Parameters) extends Node[Parameters]("bru") {
  override protected def cfg: NodeConfig = NodeConfig(
    selector = NodeSelector(
      BruDims.ISA -> p(ISA).name
    )
  )

  val fuReq    = inD[FuReq]
  val fuResp   = outD[FuResp]
  val flush    = in[FuFlushReq]
  val resolved = outV[BruResolveBundle]

  private val isaImpl  = BruIsaFactory.select(cfg)
  private val validReg = RegInit(false.B)
  private val uopReg   = Reg(new FuReq)

  fuReq.in.ready   := !flush.in.flush && (!validReg || fuResp.out.fire)
  fuResp.out.valid := validReg && !flush.in.flush

  when(flush.in.flush) {
    validReg := false.B
  }.elsewhen(fuReq.in.fire) {
    validReg := true.B
    uopReg   := fuReq.in.bits
  }.elsewhen(fuResp.out.fire) {
    validReg := false.B
  }

  private val ctrl          = isaImpl.decode(uopReg.uop)
  private val immValue      = uopReg.imm
  private val branchTaken   = isaImpl.taken(uopReg.rs1_data, uopReg.rs2_data, ctrl.op)
  private val resolvedTaken = ctrl.is_jump || branchTaken
  private val branchTarget  = isaImpl.target(uopReg.pc, uopReg.rs1_data, immValue, ctrl)
  private val fallthrough   = uopReg.pc + p(PCStep).U(p(XLen).W)
  private val actualTarget  = Mux(resolvedTaken, branchTarget, fallthrough)

  private val resp = Wire(new FuResp)

  resp.result       := fallthrough
  resp.rd           := uopReg.rd
  resp.pc           := uopReg.pc
  resp.instr        := uopReg.instr
  resp.rob_tag      := uopReg.rob_tag
  resp.trap_req     := false.B
  resp.trap_target  := 0.U
  resp.trap_ret     := false.B
  resp.trap_ret_tgt := 0.U

  fuResp.out.bits := resp

  resolved.out.valid            := validReg && !flush.in.flush
  resolved.out.bits.pc          := uopReg.pc
  resolved.out.bits.instr       := uopReg.instr
  resolved.out.bits.rob_tag     := uopReg.rob_tag
  resolved.out.bits.taken       := resolvedTaken
  resolved.out.bits.target      := actualTarget
  resolved.out.bits.fallthrough := fallthrough
}
