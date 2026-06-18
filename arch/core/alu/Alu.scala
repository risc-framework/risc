package arch.core.alu

import arch.configs._
import arch.core.exception.ExceptionCsrReq
import arch.core.fupool.{ FuReq, FuResp }
import chisel3._
import vutils.fsm.Moore
import vutils.graph.{ Node, NodeConfig, NodeSelector }

object AluState extends ChiselEnum {
  val IDLE, RESP = Value
}

class Alu(implicit p: Parameters) extends Node[Parameters]("alu") with Moore {
  override protected def cfg: NodeConfig = NodeConfig(
    selector = NodeSelector(
      AluDims.ISA -> p(ISA).name
    )
  )

  val fuReq  = inD[FuReq]
  val fuResp = outD[FuResp]
  val flush  = in[ExceptionCsrReq]

  private val isaImpl = AluIsaFactory.select(cfg)
  private val uopReg  = Reg(new FuReq)

  private val fsm = moore(AluState.IDLE, clear = flush.in.flush) { g =>
    import g._

    val IDLE = state(AluState.IDLE)
    val RESP = state(AluState.RESP)

    trans(IDLE -> RESP, fuReq.in.fire) {
      uopReg := fuReq.in.bits
    }

    trans(RESP -> RESP, fuResp.out.fire && fuReq.in.fire) {
      uopReg := fuReq.in.bits
    }

    trans(RESP -> IDLE, fuResp.out.fire && !fuReq.in.fire)
  }

  fuReq.in.ready   := !flush.in.flush && (fsm(AluState.IDLE).active || (fsm(
    AluState.RESP
  ).active && fuResp.out.ready))
  fuResp.out.valid := fsm(AluState.RESP).active && !flush.in.flush

  private val result = isaImpl.execute(uopReg)
  private val resp   = WireDefault(0.U.asTypeOf(new FuResp))

  resp.result      := result
  resp.rd          := uopReg.rd
  resp.pc          := uopReg.pc
  resp.instr       := uopReg.instr
  resp.rob_tag     := uopReg.rob_tag
  resp.trap_req    := false.B
  resp.trap_kind   := 0.U
  resp.trap_target := 0.U

  fuResp.out.bits := resp
}
