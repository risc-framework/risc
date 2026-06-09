package arch.core.regfile

import arch.configs._
import vutils.graph.NodeDims

object RegfileDims extends NodeDims("regfile") {
  val ISA = dim("isa")
}

trait RegfileIsaImpl extends RegfileDims.ISA.Impl {
  def initValue(addr: Int)(implicit p: Parameters): BigInt =
    0

  def regName(addr: Int)(implicit p: Parameters): String =
    s"x$addr"
}

object RegfileIsaFactory extends RegfileDims.ISA.Registry[RegfileIsaImpl]

object RegfileInit {
  val rv32i  = impls.isa.rv32i.RegfileRv32iIsa.registered
  val rv32im = impls.isa.rv32im.RegfileRv32imIsa.registered
}
