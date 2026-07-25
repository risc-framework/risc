package arch.core.bru

import arch.configs._
import vutils.graph.NodeDims
import chisel3._

object BruDims extends NodeDims("bru") {
  val ISA = dim("isa")
}

trait BruIsaImpl extends BruDims.ISA.Impl {
  def opWidth: Int
  def hasJump: Boolean
  def hasJalr: Boolean

  def decode(uop: UInt): BruCtrl
  def taken(src1: UInt, src2: UInt, op: UInt)(implicit p: Parameters): Bool

  def target(pc: UInt, src1: UInt, imm: UInt, ctrl: BruCtrl)(implicit p: Parameters): UInt =
    if (hasJalr) {
      val base       = Mux(ctrl.is_jalr, src1, pc)
      val rawTarget  = base + imm
      val jalrTarget = rawTarget & ~1.U(p(XLen).W)
      Mux(ctrl.is_jalr, jalrTarget, rawTarget)
    } else {
      pc + imm
    }
}

object BruIsaFactory extends BruDims.ISA.Registry[BruIsaImpl]

object BruInit {
  val rv32i  = impls.isa.rv32i.BruRv32iIsa.registered
  val rv32im = impls.isa.rv32im.BruRv32imIsa.registered
}
