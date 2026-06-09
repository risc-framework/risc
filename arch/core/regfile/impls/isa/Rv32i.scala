package arch.core.regfile.impls.isa.rv32i

import arch.configs._
import arch.core.regfile._
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }

object RegfileRv32iIsa extends RegisteredNodeUtils[RegfileIsaImpl] {
  override def utils: RegfileIsaImpl = new RegfileIsaImpl {
    override def value: String = "rv32i"

    override def initValue(addr: Int)(implicit p: Parameters): BigInt =
      0

    override def regName(addr: Int)(implicit p: Parameters): String =
      s"x$addr"
  }

  override def registry: NodeDimensionRegistry[RegfileIsaImpl] =
    RegfileIsaFactory
}
