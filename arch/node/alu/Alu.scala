package arch.node.alu

import arch.configs._
import arch.node.fupool.{ FuIO, FuResp }
import arch.node.uop.MicroOp
import vutils.graph.{ Node, NodeType, NodeConfig, NodeSelector }
import chisel3._

class Alu(implicit p: Parameters) extends Node(new FuIO) {
  private val cfg = NodeConfig(
    selector = NodeSelector(
      AluDims.ISA -> p(ISA).name
    )
  )

  override def nodeType: NodeType  = AluMeta.Type
  override def implName: String    = s"alu_${cfg.selector.canonicalName}"
  override def desiredName: String = implName

  private val isaImpl  = AluIsaFactory.select(cfg)
  private val validReg = RegInit(false.B)
  private val uopReg   = Reg(new MicroOp)

  io.req.ready  := !io.flush && (!validReg || io.resp.fire)
  io.resp.valid := validReg && !io.flush

  when(io.flush) {
    validReg := false.B
  }.elsewhen(io.req.fire) {
    validReg := true.B
    uopReg   := io.req.bits
  }.elsewhen(io.resp.fire) {
    validReg := false.B
  }

  private val result = isaImpl.execute(uopReg)
  private val resp   = Wire(new FuResp)

  resp.result  := result
  resp.rd      := uopReg.rd
  resp.pc      := uopReg.pc
  resp.instr   := uopReg.instr
  resp.rob_tag := uopReg.rob_tag

  io.resp.bits := resp
}

import vutils._
import arch.node.imm.ImmInit

object AluNode extends App {
  ImmInit
  AluInit

  DesignEmitter.emit(
    gen = new Alu,
    filename = "alu",
    target = SystemVerilog,
    info = true,
    lowering = true,
  )
}
