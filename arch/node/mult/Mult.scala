package arch.node.mult

import arch.configs._
import arch.node.fupool.{ FuIO, FuResp }
import arch.node.uop.MicroOp
import vutils.graph.{ Node, NodeType, NodeConfig, NodeSelector }
import vutils.math.mul.IntegerMultiplier
import chisel3._
import chisel3.util.{ switch, is }

class MultIO(implicit p: Parameters) extends Bundle {
  val fu = new FuIO
}

object MultState extends ChiselEnum {
  val IDLE, BUSY, DONE = Value
}

class Mult(implicit p: Parameters) extends Node(new MultIO) {
  private val cfg = NodeConfig(
    selector = NodeSelector(
      MultDims.ISA -> p(ISA).name
    )
  )

  override def nodeType: NodeType  = MultMeta.Type
  override def desiredName: String = s"mult_${cfg.selector.canonicalName}"

  private val isaImpl    = MultIsaFactory.select(cfg)
  private val multiplier = Module(new IntegerMultiplier(p(XLen), p(MultPipelineStages)))
  private val state      = RegInit(MultState.IDLE)
  private val uopReg     = Reg(new MicroOp)
  private val resultReg  = RegInit(0.U(p(XLen).W))

  private val ctrl = isaImpl.decode(io.fu.req.bits.uop)

  io.fu.req.ready := !io.fu.flush && state === MultState.IDLE && multiplier.io.in.ready

  multiplier.io.kill                 := io.fu.flush
  multiplier.io.in.valid             := io.fu.req.valid && io.fu.req.ready
  multiplier.io.in.bits.multiplicand := io.fu.req.bits.rs1_data
  multiplier.io.in.bits.multiplier   := io.fu.req.bits.rs2_data
  multiplier.io.in.bits.aSigned      := ctrl.a_signed
  multiplier.io.in.bits.bSigned      := ctrl.b_signed
  multiplier.io.in.bits.takeHigh     := ctrl.high
  multiplier.io.out.ready            := !io.fu.flush && state === MultState.BUSY

  when(io.fu.flush) {
    state := MultState.IDLE
  }.otherwise {
    switch(state) {
      is(MultState.IDLE) {
        when(io.fu.req.fire) {
          uopReg := io.fu.req.bits
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
        when(io.fu.resp.fire) {
          state := MultState.IDLE
        }
      }
    }
  }

  private val resp = Wire(new FuResp)

  resp.result  := resultReg
  resp.rd      := uopReg.rd
  resp.pc      := uopReg.pc
  resp.instr   := uopReg.instr
  resp.rob_tag := uopReg.rob_tag

  io.fu.resp.valid := state === MultState.DONE && !io.fu.flush
  io.fu.resp.bits  := resp
}

import arch.node.imm._
import vutils._

object MultNode extends App {
  ImmInit
  MultInit

  DesignEmitter.emit(
    gen = new Mult,
    filename = "mult",
    target = SystemVerilog,
    info = true,
    lowering = true,
  )
}
