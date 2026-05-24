package arch.core.div

import arch.configs._
import arch.core.ooo._
import chisel3._
import chisel3.util.{ is, switch }

object DivFuState extends ChiselEnum {
  val IDLE, BUSY, DONE = Value
}

class DivFU(implicit p: Parameters) extends FunctionalUnit {
  override def desiredName: String = s"${p(ISA).name}_div_fu"

  private val div       = Module(new Div)
  private val div_utils = DivUtilsFactory.getOrThrow(p(ISA).name)

  private val uop_reg    = Reg(new MicroOp)
  private val result_reg = RegInit(0.U(p(XLen).W))
  private val state      = RegInit(DivFuState.IDLE)

  private val can_accept = state === DivFuState.IDLE && !io.flush
  private val ctrl       = div_utils.decode(io.req.bits.uop)

  io.req.ready := can_accept && div.io.req.ready

  div.io.kill := io.flush

  div.io.req.valid     := io.req.valid && can_accept
  div.io.req.bits.src1 := io.req.bits.rs1_data
  div.io.req.bits.src2 := io.req.bits.rs2_data
  div.io.req.bits.ctrl := ctrl

  div.io.resp.ready := state === DivFuState.BUSY && !io.flush

  when(io.flush) {
    state := DivFuState.IDLE
  }.otherwise {
    switch(state) {
      is(DivFuState.IDLE) {
        when(io.req.fire) {
          uop_reg := io.req.bits
          state   := DivFuState.BUSY
        }
      }

      is(DivFuState.BUSY) {
        when(div.io.resp.fire) {
          result_reg := div.io.resp.bits.result
          state      := DivFuState.DONE
        }
      }

      is(DivFuState.DONE) {
        when(io.resp.fire) {
          state := DivFuState.IDLE
        }
      }
    }
  }

  io.resp.valid        := state === DivFuState.DONE
  io.resp.bits.result  := result_reg
  io.resp.bits.rd      := uop_reg.rd
  io.resp.bits.pc      := uop_reg.pc
  io.resp.bits.instr   := uop_reg.instr
  io.resp.bits.rob_tag := uop_reg.rob_tag
}
