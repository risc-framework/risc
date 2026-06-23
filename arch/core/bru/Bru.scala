package arch.core.bru

import arch.configs._
import arch.core.fupool.{ FuReq, FuResp }
import chisel3._
import vutils.fsm.Moore
import vutils.graph.{ Node, NodeConfig, NodeSelector }

object BruState extends ChiselEnum {
  val IDLE, RESP = Value
}

class Bru(implicit p: Parameters) extends Node[Parameters]("bru") with Moore {
  override protected def cfg: NodeConfig = NodeConfig(
    selector = NodeSelector(
      BruDims.ISA -> p(ISA).name
    )
  )

  val fuReq    = inD[FuReq]
  val fuResp   = outD[FuResp]
  val flush    = in[Bool]
  val resolved = outV[BruResolveBundle]

  private val isaImpl = BruIsaFactory.select(cfg)
  private val uopReg  = Reg(new FuReq)

  private val fsm = moore(BruState.IDLE, clear = flush.in) { g =>
    import g._

    val IDLE = state(BruState.IDLE)
    val RESP = state(BruState.RESP)

    trans(IDLE -> RESP, fuReq.in.fire) {
      uopReg := fuReq.in.bits
    }

    trans(RESP -> RESP, fuResp.out.fire && fuReq.in.fire) {
      uopReg := fuReq.in.bits
    }

    trans(RESP -> IDLE, fuResp.out.fire && !fuReq.in.fire)
  }

  fuReq.in.ready   := !flush.in && (fsm(BruState.IDLE).active || (fsm(
    BruState.RESP
  ).active && fuResp.out.ready))
  fuResp.out.valid := fsm(BruState.RESP).active && !flush.in

  private val ctrl          = isaImpl.decode(uopReg.uop)
  private val immValue      = uopReg.imm
  private val branchTaken   = isaImpl.taken(uopReg.rs1_data, uopReg.rs2_data, ctrl.op)
  private val resolvedTaken = ctrl.is_jump || branchTaken
  private val branchTarget  = isaImpl.target(uopReg.pc, uopReg.rs1_data, immValue, ctrl)
  private val fallthrough   = uopReg.pc + p(PCStep).U(p(XLen).W)
  private val actualTarget  = Mux(resolvedTaken, branchTarget, fallthrough)

  fuResp.out.bits.result  := fallthrough
  fuResp.out.bits.rd      := uopReg.rd
  fuResp.out.bits.pc      := uopReg.pc
  fuResp.out.bits.instr   := uopReg.instr
  fuResp.out.bits.rob_tag := uopReg.rob_tag

  resolved.out.valid            := fsm(BruState.RESP).active && !flush.in
  resolved.out.bits.pc          := uopReg.pc
  resolved.out.bits.instr       := uopReg.instr
  resolved.out.bits.rob_tag     := uopReg.rob_tag
  resolved.out.bits.taken       := resolvedTaken
  resolved.out.bits.target      := actualTarget
  resolved.out.bits.fallthrough := fallthrough
}
