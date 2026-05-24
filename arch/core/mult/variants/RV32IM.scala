package arch.core.mult.riscv

import arch.core.mult._
import arch.configs._
import chisel3._
import chisel3.util.BitPat

// Format: uop[7:3] = 0 | uop[2] = signed_bit | uop[1] = signed_bit | uop[0] = bit_sel
trait RV32IMMultUOpConsts {
  private def cat(bps: BitPat*): BitPat = bps.reduce(_ ## _)
  private def N                         = BitPat("b0")
  private def Y                         = BitPat("b1")
  private def P_X                       = BitPat("b?????")

  def UOP_MUL    = cat(P_X, Y, Y, N)
  def UOP_MULH   = cat(P_X, Y, Y, Y)
  def UOP_MULHSU = cat(P_X, Y, N, Y)
  def UOP_MULHU  = cat(P_X, N, N, Y)
}

object RV32IMMultUtils extends RegisteredUtils[MultUtils] with RV32IMMultUOpConsts {
  override def utils: MultUtils = new MultUtils {
    override def name: String = "rv32im"

    override def decode(uop: UInt): MultCtrl = {
      val ctrl = Wire(new MultCtrl)
      ctrl.a_signed := uop(2)
      ctrl.b_signed := uop(1)
      ctrl.high     := uop(0)
      ctrl
    }

  }

  override def factory: UtilsFactory[MultUtils] = MultUtilsFactory
}
