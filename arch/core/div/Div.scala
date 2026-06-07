package arch.core.div

import arch.configs._
import arch.core.fupool.{ FuIO, FuResp, FuReq }
import vutils.graph.{ Node, NodeType, NodeConfig, NodeSelector }
import vutils.math.div.RestoringDivider
import chisel3._
import chisel3.util.{ switch, is }

class DivIO(implicit p: Parameters) extends Bundle {
  val fu = new FuIO
}

object DivState extends ChiselEnum {
  val IDLE, BUSY, DONE = Value
}

class Div(implicit p: Parameters) extends Node(new DivIO) {
  private val cfg = NodeConfig(
    selector = NodeSelector(
      DivDims.ISA -> p(ISA).name
    )
  )

  override def nodeType: NodeType  = DivMeta.Type
  override def desiredName: String = s"div_${cfg.selector.canonicalName}"

  private val isaImpl   = DivIsaFactory.select(cfg)
  private val divider   = Module(new RestoringDivider(p(XLen)))
  private val state     = RegInit(DivState.IDLE)
  private val uopReg    = Reg(new FuReq)
  private val resultReg = RegInit(0.U(p(XLen).W))

  private val ctrl = isaImpl.decode(io.fu.req.bits.uop)

  io.fu.req.ready := !io.fu.flush && state === DivState.IDLE && divider.io.in.ready

  divider.io.kill                    := io.fu.flush
  divider.io.in.valid                := io.fu.req.valid && io.fu.req.ready
  divider.io.in.bits.dividend        := io.fu.req.bits.rs1_data
  divider.io.in.bits.divisor         := io.fu.req.bits.rs2_data
  divider.io.in.bits.signed          := ctrl.is_signed
  divider.io.in.bits.selectRemainder := ctrl.is_rem
  divider.io.out.ready               := !io.fu.flush && state === DivState.BUSY

  when(io.fu.flush) {
    state := DivState.IDLE
  }.otherwise {
    switch(state) {
      is(DivState.IDLE) {
        when(io.fu.req.fire) {
          uopReg := io.fu.req.bits
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
        when(io.fu.resp.fire) {
          state := DivState.IDLE
        }
      }
    }
  }

  private val resp = Wire(new FuResp)

  resp.result       := resultReg
  resp.rd           := uopReg.rd
  resp.pc           := uopReg.pc
  resp.instr        := uopReg.instr
  resp.rob_tag      := uopReg.rob_tag
  resp.trap_req     := false.B
  resp.trap_target  := 0.U
  resp.trap_ret     := false.B
  resp.trap_ret_tgt := 0.U

  io.fu.resp.valid := state === DivState.DONE && !io.fu.flush
  io.fu.resp.bits  := resp
}
