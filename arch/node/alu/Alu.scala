package arch.node.alu

import arch.configs._
import arch.node.fupool.{ FuIO, FuResp }
import arch.node.uop.MicroOp
import vutils.graph.{ Node, NodeType, NodeConfig, NodeSelector }
import chisel3._

class AluIO(implicit p: Parameters) extends Bundle {
  val fu = new FuIO
}

class Alu(implicit p: Parameters) extends Node(new AluIO) {
  private val cfg = NodeConfig(
    selector = NodeSelector(
      AluDims.ISA -> p(ISA).name
    )
  )

  override def nodeType: NodeType  = AluMeta.Type
  override def desiredName: String = s"alu_${cfg.selector.canonicalName}"

  private val isaImpl  = AluIsaFactory.select(cfg)
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

  private val result = isaImpl.execute(uopReg)
  private val resp   = Wire(new FuResp)

  resp.result  := result
  resp.rd      := uopReg.rd
  resp.pc      := uopReg.pc
  resp.instr   := uopReg.instr
  resp.rob_tag := uopReg.rob_tag

  io.fu.resp.bits := resp
}
