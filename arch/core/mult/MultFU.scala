package arch.core.mult

import arch.configs._
import arch.core.ooo._
import chisel3._
import chisel3.util.{ is, switch }

object MultFuState extends ChiselEnum {
  val IDLE, BUSY, DONE = Value
}

class MultFU(implicit p: Parameters) extends FunctionalUnit {
  override def desiredName: String = s"${p(ISA).name}_mult_fu"

  private val mult       = Module(new Mult)
  private val mult_utils = MultUtilsFactory.getOrThrow(p(ISA).name)

  private val uop_reg    = Reg(new MicroOp)
  private val result_reg = RegInit(0.U(p(XLen).W))
  private val state      = RegInit(MultFuState.IDLE)

  private val can_accept = state === MultFuState.IDLE && !io.flush
  private val ctrl       = mult_utils.decode(io.req.bits.uop)

  io.req.ready := can_accept && mult.io.req.ready

  mult.io.kill := io.flush

  mult.io.req.valid     := io.req.valid && can_accept
  mult.io.req.bits.src1 := io.req.bits.rs1_data
  mult.io.req.bits.src2 := io.req.bits.rs2_data
  mult.io.req.bits.ctrl := ctrl

  mult.io.resp.ready := state === MultFuState.BUSY && !io.flush

  when(io.flush) {
    state := MultFuState.IDLE
  }.otherwise {
    switch(state) {
      is(MultFuState.IDLE) {
        when(io.req.fire) {
          uop_reg := io.req.bits
          state   := MultFuState.BUSY
        }
      }

      is(MultFuState.BUSY) {
        when(mult.io.resp.fire) {
          result_reg := mult.io.resp.bits.result
          state      := MultFuState.DONE
        }
      }

      is(MultFuState.DONE) {
        when(io.resp.fire) {
          state := MultFuState.IDLE
        }
      }
    }
  }

  io.resp.valid        := state === MultFuState.DONE
  io.resp.bits.result  := result_reg
  io.resp.bits.rd      := uop_reg.rd
  io.resp.bits.pc      := uop_reg.pc
  io.resp.bits.instr   := uop_reg.instr
  io.resp.bits.rob_tag := uop_reg.rob_tag
}
