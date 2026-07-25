package arch.core.regfile.impls.isa.rv32im

import arch.configs._
import arch.core.regfile._
import arch.core.regfile.impls.isa.rv32i.RegfileRv32iIsa
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }

object RegfileRv32imIsa extends RegisteredNodeUtils[RegfileIsaImpl] {
  override def utils: RegfileIsaImpl = new RegfileIsaImpl {
    override def value: String = "rv32im"
    private val rv32i          = RegfileRv32iIsa.utils

    override def initValue(addr: Int)(implicit p: Parameters): BigInt = rv32i.initValue(addr)

    override def regName(addr: Int)(implicit p: Parameters): String = rv32i.regName(addr)

  }

  override def registry: NodeDimensionRegistry[RegfileIsaImpl] =
    RegfileIsaFactory
}
