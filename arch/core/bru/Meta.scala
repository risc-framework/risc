package arch.core.bru

import arch.core.fupool.FuIO
import arch.configs._
import vutils.graph.{ NodeType, NodeDim, NodeDimensionImpl, NodeDimensionRegistry, NodePort }
import chisel3._

object BruMeta {
  val Type    = NodeType("bru")
  val FU      = NodePort[BruIO, FuIO]("fu", _.fu)
  val RESOLVE = NodePort[BruIO, BruResolveIO]("resolve", _.resolve)
}

object BruDims {
  val ISA = NodeDim("isa")
}

trait BruIsaImpl extends NodeDimensionImpl {
  override def nodeType: NodeType = BruMeta.Type
  override def dim: NodeDim       = BruDims.ISA
  override def name: String       = value

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

object BruIsaFactory extends NodeDimensionRegistry[BruIsaImpl](BruMeta.Type, BruDims.ISA)

object BruInit {
  val rv32i  = impls.isa.rv32i.BruRv32iIsa
  val rv32im = impls.isa.rv32im.BruRv32imIsa
}
