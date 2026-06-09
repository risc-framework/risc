package arch.core.mult

import vutils.graph.NodeDims
import chisel3._

object MultDims extends NodeDims("mult") {
  val ISA = dim("isa")
}

trait MultIsaImpl extends MultDims.ISA.Impl {
  def decode(uop: UInt): MultCtrl
}

object MultIsaFactory extends MultDims.ISA.Registry[MultIsaImpl]

object MultInit {
  val rv32i  = impls.isa.rv32i.MultRv32iIsa.registered
  val rv32im = impls.isa.rv32im.MultRv32imIsa.registered
}
