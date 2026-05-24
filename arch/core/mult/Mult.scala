package arch.core.mult

import arch.configs._
import vutils.math.mul.IntegerMultiplier
import chisel3._
import chisel3.util.Decoupled

class MultCtrl extends Bundle {
  val a_signed = Bool()
  val b_signed = Bool()
  val high     = Bool()
}

class MultReq(implicit p: Parameters) extends Bundle {
  val src1 = UInt(p(XLen).W)
  val src2 = UInt(p(XLen).W)
  val ctrl = new MultCtrl
}

class MultResp(implicit p: Parameters) extends Bundle {
  val result = UInt(p(XLen).W)
}

class MultIO(implicit p: Parameters) extends Bundle {
  val req  = Flipped(Decoupled(new MultReq))
  val resp = Decoupled(new MultResp)

  val kill = Input(Bool())
  val busy = Output(Bool())
}

class Mult(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_mult"

  val io = IO(new MultIO)

  private val multiplier =
    Module(new IntegerMultiplier(p(XLen), p(MultPipelineStages)))

  multiplier.io.kill := io.kill

  multiplier.io.in.valid             := io.req.valid && !io.kill
  multiplier.io.in.bits.multiplicand := io.req.bits.src1
  multiplier.io.in.bits.multiplier   := io.req.bits.src2
  multiplier.io.in.bits.aSigned      := io.req.bits.ctrl.a_signed
  multiplier.io.in.bits.bSigned      := io.req.bits.ctrl.b_signed
  multiplier.io.in.bits.takeHigh     := io.req.bits.ctrl.high

  io.req.ready := multiplier.io.in.ready && !io.kill

  io.resp.valid           := multiplier.io.out.valid && !io.kill
  io.resp.bits.result     := multiplier.io.out.bits.result
  multiplier.io.out.ready := io.resp.ready && !io.kill

  io.busy := multiplier.io.busy
}
