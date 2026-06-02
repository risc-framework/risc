package arch.core.div.impls.isa.rv32im

import arch.core.div._
import vutils.graph.{ NodeRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.BitPat

trait Rv32imDivUopConsts {
  private def cat(bps: BitPat*): BitPat = bps.reduce(_ ## _)
  private def N                         = BitPat("b0")
  private def Y                         = BitPat("b1")
  private def P_X                       = BitPat("b??????")

  def UOP_DIV  = cat(P_X, Y, N)
  def UOP_DIVU = cat(P_X, N, N)
  def UOP_REM  = cat(P_X, Y, Y)
  def UOP_REMU = cat(P_X, N, Y)
}

object DivRv32imIsa extends RegisteredNodeUtils[DivIsaImpl] with Rv32imDivUopConsts {
  override def utils: DivIsaImpl = new DivIsaImpl {
    override def value: String = "rv32im"

    override def decode(uop: UInt): DivCtrl = {
      val ctrl = Wire(new DivCtrl)
      ctrl.is_signed := uop(1)
      ctrl.is_rem    := uop(0)
      ctrl
    }
  }

  override def registry: NodeRegistry[DivIsaImpl] = DivIsaFactory
}
