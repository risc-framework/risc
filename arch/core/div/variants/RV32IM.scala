package arch.core.div.riscv

import arch.core.div._
import arch.configs._
import chisel3._
import chisel3.util.BitPat

// Format: uop[7:2] = 0 | uop[1] = signed_bit | uop[0] = rem_bit
trait RV32IMDivUOpConsts {
  private def cat(bps: BitPat*): BitPat = bps.reduce(_ ## _)
  private def N                         = BitPat("b0")
  private def Y                         = BitPat("b1")
  private def P_X                       = BitPat("b??????")

  def UOP_DIV  = cat(P_X, Y, N)
  def UOP_DIVU = cat(P_X, N, N)
  def UOP_REM  = cat(P_X, Y, Y)
  def UOP_REMU = cat(P_X, N, Y)
}

object RV32IMDivUtils extends RegisteredUtils[DivUtils] with RV32IMDivUOpConsts {
  override def utils: DivUtils = new DivUtils {
    override def name: String = "rv32im"

    override def decode(uop: UInt): DivCtrl = {
      val ctrl = Wire(new DivCtrl)
      ctrl.is_signed := uop(1)
      ctrl.is_rem    := uop(0)
      ctrl
    }
  }

  override def factory: UtilsFactory[DivUtils] = DivUtilsFactory
}
