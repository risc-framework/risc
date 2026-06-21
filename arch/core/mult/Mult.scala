package arch.core.mult

import arch.configs._
import arch.core.fupool.{ FuResp, FuReq }
import arch.core.exception.ExceptionCsrReq
import vutils.graph.{ Node, NodeConfig, NodeSelector }
import vutils.math.mul.IntegerMultiplier
import chisel3._
import chisel3.util.{ switch, is }

object MultState extends ChiselEnum {
  val IDLE, BUSY, DONE = Value
}

class Mult(implicit p: Parameters) extends Node[Parameters]("mult") {
  override protected def cfg: NodeConfig = NodeConfig(
    selector = NodeSelector(
      MultDims.ISA -> p(ISA).name
    )
  )

  val fuReq  = inD[FuReq]
  val fuResp = outD[FuResp]
  val flush  = in[ExceptionCsrReq]

  private val isaImpl    = MultIsaFactory.select(cfg)
  private val multiplier = Module(new IntegerMultiplier(p(XLen), p(MultPipelineStages)))
  private val state      = RegInit(MultState.IDLE)
  private val uopReg     = Reg(new FuReq)
  private val resultReg  = RegInit(0.U(p(XLen).W))

  private val ctrl = isaImpl.decode(fuReq.in.bits.uop)

  fuReq.in.ready := !flush.in.flush && state === MultState.IDLE && multiplier.io.in.ready

  multiplier.io.kill                 := flush.in.flush
  multiplier.io.in.valid             := !flush.in.flush && state === MultState.IDLE && fuReq.in.valid
  multiplier.io.in.bits.multiplicand := fuReq.in.bits.rs1_data
  multiplier.io.in.bits.multiplier   := fuReq.in.bits.rs2_data
  multiplier.io.in.bits.aSigned      := ctrl.a_signed
  multiplier.io.in.bits.bSigned      := ctrl.b_signed
  multiplier.io.in.bits.takeHigh     := ctrl.high
  multiplier.io.out.ready            := !flush.in.flush && state === MultState.BUSY

  when(flush.in.flush) {
    state := MultState.IDLE
  }.otherwise {
    switch(state) {
      is(MultState.IDLE) {
        when(fuReq.in.fire) {
          uopReg := fuReq.in.bits
          state  := MultState.BUSY
        }
      }

      is(MultState.BUSY) {
        when(multiplier.io.out.fire) {
          resultReg := multiplier.io.out.bits.result
          state     := MultState.DONE
        }
      }

      is(MultState.DONE) {
        when(fuResp.out.fire) {
          state := MultState.IDLE
        }
      }
    }
  }

  fuResp.out.valid := state === MultState.DONE && !flush.in.flush

  fuResp.out.bits.result  := resultReg
  fuResp.out.bits.rd      := uopReg.rd
  fuResp.out.bits.pc      := uopReg.pc
  fuResp.out.bits.instr   := uopReg.instr
  fuResp.out.bits.rob_tag := uopReg.rob_tag
}
