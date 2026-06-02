package arch.core.bru.impls.isa.rv32im

import arch.core.bru._
import arch.core.bru.impls.isa.rv32i.BruRv32iIsa
import arch.configs._
import vutils.graph.{ NodeRegistry, RegisteredNodeUtils }
import chisel3._

object BruRv32imIsa extends RegisteredNodeUtils[BruIsaImpl] {
  override def utils: BruIsaImpl = new BruIsaImpl {
    private val rv32i = BruRv32iIsa.utils

    override def value: String                                                         = "rv32im"
    override def opWidth: Int                                                          = rv32i.opWidth
    override def hasJump: Boolean                                                      = rv32i.hasJump
    override def hasJalr: Boolean                                                      = rv32i.hasJalr
    override def decode(uop: UInt): BruCtrl                                            = rv32i.decode(uop)
    override def taken(src1: UInt, src2: UInt, op: UInt)(implicit p: Parameters): Bool =
      rv32i.taken(src1, src2, op)
  }

  override def registry: NodeRegistry[BruIsaImpl] = BruIsaFactory
}
