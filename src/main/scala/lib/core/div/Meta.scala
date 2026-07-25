package arch.core.div

import vutils.graph.NodeDims
import chisel3._

object DivDims extends NodeDims("div") {
  val ISA = dim("isa")
}

trait DivIsaImpl extends DivDims.ISA.Impl {
  def decode(uop: UInt): DivCtrl
}

object DivIsaFactory extends DivDims.ISA.Registry[DivIsaImpl]

object DivInit {
  val rv32i  = impls.isa.rv32i.DivRv32iIsa.registered
  val rv32im = impls.isa.rv32im.DivRv32imIsa.registered
}
