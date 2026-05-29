package arch.node.mult.impls.isa.rv32im

import arch.node.mult._
import vutils.graph.{ NodeRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.BitPat

trait Rv32imMultUopConsts {
  private def cat(bps: BitPat*): BitPat = bps.reduce(_ ## _)
  private def N                         = BitPat("b0")
  private def Y                         = BitPat("b1")
  private def P_X                       = BitPat("b?????")

  def UOP_MUL    = cat(P_X, Y, Y, N)
  def UOP_MULH   = cat(P_X, Y, Y, Y)
  def UOP_MULHSU = cat(P_X, Y, N, Y)
  def UOP_MULHU  = cat(P_X, N, N, Y)
}

object MultRv32imIsa extends RegisteredNodeUtils[MultIsaImpl] with Rv32imMultUopConsts {
  override def utils: MultIsaImpl = new MultIsaImpl {
    override def value: String = "rv32im"

    override def decode(uop: UInt): MultCtrl = {
      val ctrl = Wire(new MultCtrl)
      ctrl.a_signed := uop(2)
      ctrl.b_signed := uop(1)
      ctrl.high     := uop(0)
      ctrl
    }
  }

  override def registry: NodeRegistry[MultIsaImpl] = MultIsaFactory
}
