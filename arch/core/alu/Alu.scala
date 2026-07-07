package arch.core.alu

import arch.configs._
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
  val flush  = in[Bool]

  private val isaImpl = AluIsaFactory.select(cfg)
  private val uopReg  = Reg(new FuReq)

  private val fsm = moore(AluState.IDLE, clear = flush.in) { g =>
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

  fuReq.in.ready   := !flush.in && (fsm(AluState.IDLE).active || (fsm(
    AluState.RESP
  ).active && fuResp.out.ready))
  fuResp.out.valid := fsm(AluState.RESP).active && !flush.in

  private val result = isaImpl.execute(uopReg)

  fuResp.out.bits.result  := result
  fuResp.out.bits.rd      := uopReg.rd
  fuResp.out.bits.pc      := uopReg.pc
  fuResp.out.bits.instr   := uopReg.instr
  fuResp.out.bits.rob_tag := uopReg.rob_tag
}
