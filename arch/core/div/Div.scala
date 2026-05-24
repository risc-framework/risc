package arch.core.div

import arch.configs._
import vutils.math.div.RestoringDivider
import chisel3._
import chisel3.util.Decoupled

class DivCtrl extends Bundle {
  val is_signed = Bool()
  val is_rem    = Bool()
}

class DivReq(implicit p: Parameters) extends Bundle {
  val src1 = UInt(p(XLen).W)
  val src2 = UInt(p(XLen).W)
  val ctrl = new DivCtrl
}

class DivResp(implicit p: Parameters) extends Bundle {
  val result      = UInt(p(XLen).W)
  val quotient    = UInt(p(XLen).W)
  val remainder   = UInt(p(XLen).W)
  val div_by_zero = Bool()
}

class DivIO(implicit p: Parameters) extends Bundle {
  val req  = Flipped(Decoupled(new DivReq))
  val resp = Decoupled(new DivResp)

  val kill = Input(Bool())
  val busy = Output(Bool())
}

class Div(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_divider"

  val io = IO(new DivIO)

  private val divider = Module(new RestoringDivider(p(XLen)))

  divider.io.kill := io.kill

  divider.io.in.valid                := io.req.valid && !io.kill
  divider.io.in.bits.dividend        := io.req.bits.src1
  divider.io.in.bits.divisor         := io.req.bits.src2
  divider.io.in.bits.signed          := io.req.bits.ctrl.is_signed
  divider.io.in.bits.selectRemainder := io.req.bits.ctrl.is_rem

  io.req.ready := divider.io.in.ready && !io.kill

  io.resp.valid            := divider.io.out.valid && !io.kill
  io.resp.bits.result      := divider.io.out.bits.result
  io.resp.bits.quotient    := divider.io.out.bits.quotient
  io.resp.bits.remainder   := divider.io.out.bits.remainder
  io.resp.bits.div_by_zero := divider.io.out.bits.divByZero

  divider.io.out.ready := io.resp.ready && !io.kill

  io.busy := divider.io.busy
}
