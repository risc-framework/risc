package arch.core.alu

import arch.configs._
import arch.core.fupool.{ FuResp, FuReq }
import arch.core.exception.ExceptionCsrReq
import vutils.graph.{ Node, NodeConfig, NodeSelector }
import chisel3._

class Alu(implicit p: Parameters) extends Node[Parameters]("alu") {
  override protected def cfg: NodeConfig = NodeConfig(
    selector = NodeSelector(
      AluDims.ISA -> p(ISA).name
    )
  )

  val fuReq  = inD[FuReq]
  val fuResp = outD[FuResp]
  val flush  = in[ExceptionCsrReq]

  private val isaImpl  = AluIsaFactory.select(cfg)
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

  private val result = isaImpl.execute(uopReg)
  private val resp   = Wire(new FuResp)

  resp.result       := result
  resp.rd           := uopReg.rd
  resp.pc           := uopReg.pc
  resp.instr        := uopReg.instr
  resp.rob_tag      := uopReg.rob_tag
  resp.trap_req     := false.B
  resp.trap_kind    := 0.U
  resp.trap_target  := 0.U
  resp.trap_ret     := false.B
  resp.trap_ret_tgt := 0.U

  fuResp.out.bits := resp
}
