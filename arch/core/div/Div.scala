package arch.core.div

import arch.configs._
import arch.core.fupool.{ FuResp, FuReq }
import vutils.graph.{ Node, NodeConfig, NodeSelector }
import vutils.math.div.RestoringDivider
import chisel3._
import chisel3.util.{ switch, is }

object DivState extends ChiselEnum {
  val IDLE, BUSY, DONE = Value
}

class Div(implicit p: Parameters) extends Node[Parameters]("div") {
  override protected def cfg: NodeConfig = NodeConfig(
    selector = NodeSelector(
      DivDims.ISA -> p(ISA).name
    )
  )

  val fuReq  = inD[FuReq]
  val fuResp = outD[FuResp]
  val flush  = in[Bool]

  private val isaImpl   = DivIsaFactory.select(cfg)
  private val divider   = Module(new RestoringDivider(p(XLen)))
  private val state     = RegInit(DivState.IDLE)
  private val uopReg    = Reg(new FuReq)
  private val resultReg = RegInit(0.U(p(XLen).W))

  private val ctrl = isaImpl.decode(fuReq.in.bits.uop)

  fuReq.in.ready := !flush.in && state === DivState.IDLE && divider.io.in.ready

  divider.io.kill                    := flush.in
  divider.io.in.valid                := !flush.in && state === DivState.IDLE && fuReq.in.valid
  divider.io.in.bits.dividend        := fuReq.in.bits.rs1_data
  divider.io.in.bits.divisor         := fuReq.in.bits.rs2_data
  divider.io.in.bits.signed          := ctrl.is_signed
  divider.io.in.bits.selectRemainder := ctrl.is_rem
  divider.io.out.ready               := !flush.in && state === DivState.BUSY

  when(flush.in) {
    state := DivState.IDLE
  }.otherwise {
    switch(state) {
      is(DivState.IDLE) {
        when(fuReq.in.fire) {
          uopReg := fuReq.in.bits
          state  := DivState.BUSY
        }
      }

      is(DivState.BUSY) {
        when(divider.io.out.fire) {
          resultReg := divider.io.out.bits.result
          state     := DivState.DONE
        }
      }

      is(DivState.DONE) {
        when(fuResp.out.fire) {
          state := DivState.IDLE
        }
      }
    }
  }

  // The divider state, Scheduler, and ROB are all cleared on the flush edge.
  // Keep request acceptance and divider kill flush-gated, but do not put raw
  // globalFlush on the completed response path.
  fuResp.out.valid := state === DivState.DONE

  fuResp.out.bits.result  := resultReg
  fuResp.out.bits.rd      := uopReg.rd
  fuResp.out.bits.pc      := uopReg.pc
  fuResp.out.bits.instr   := uopReg.instr
  fuResp.out.bits.rob_tag := uopReg.rob_tag
}
