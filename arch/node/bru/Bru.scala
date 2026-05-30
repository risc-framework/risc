package arch.node.bru

import arch.node.fupool.{ FuIO, FuResp }
import arch.node.imm.ImmIsaFactory
import arch.node.uop.MicroOp
import arch.configs._
import vutils.graph.{ Node, NodeType, NodeConfig, NodeSelector }
import chisel3._

class BruIO(implicit p: Parameters) extends Bundle {
  val fu      = new FuIO
  val resolve = new BruResolveIO
}

class Bru(implicit p: Parameters) extends Node(new BruIO) {
  private val cfg = NodeConfig(
    selector = NodeSelector(
      BruDims.ISA -> p(ISA).name
    )
  )

  override def nodeType: NodeType  = BruMeta.Type
  override def desiredName: String = s"bru_${cfg.selector.canonicalName}"

  private val isaImpl  = BruIsaFactory.select(cfg)
  private val imm      = ImmIsaFactory.select(p(ISA).name)
  private val validReg = RegInit(false.B)
  private val uopReg   = Reg(new MicroOp)

  io.fu.req.ready  := !io.fu.flush && (!validReg || io.fu.resp.fire)
  io.fu.resp.valid := validReg && !io.fu.flush

  when(io.fu.flush) {
    validReg := false.B
  }.elsewhen(io.fu.req.fire) {
    validReg := true.B
    uopReg   := io.fu.req.bits
  }.elsewhen(io.fu.resp.fire) {
    validReg := false.B
  }

  private val ctrl          = isaImpl.decode(uopReg.uop)
  private val immValue      = imm.gen(uopReg.instr, uopReg.imm_type)
  private val branchTaken   = isaImpl.taken(uopReg.rs1_data, uopReg.rs2_data, ctrl.op)
  private val resolvedTaken = ctrl.is_jump || branchTaken
  private val branchTarget  = isaImpl.target(uopReg.pc, uopReg.rs1_data, immValue, ctrl)
  private val fallthrough   = uopReg.pc + p(PCStep).U(p(XLen).W)
  private val actualTarget  = Mux(resolvedTaken, branchTarget, fallthrough)

  private val resp = Wire(new FuResp)

  resp.result  := fallthrough
  resp.rd      := uopReg.rd
  resp.pc      := uopReg.pc
  resp.instr   := uopReg.instr
  resp.rob_tag := uopReg.rob_tag

  io.fu.resp.bits := resp

  io.resolve.resolved.valid            := validReg && !io.fu.flush
  io.resolve.resolved.bits.pc          := uopReg.pc
  io.resolve.resolved.bits.instr       := uopReg.instr
  io.resolve.resolved.bits.rob_tag     := uopReg.rob_tag
  io.resolve.resolved.bits.taken       := resolvedTaken
  io.resolve.resolved.bits.target      := actualTarget
  io.resolve.resolved.bits.fallthrough := fallthrough
}
