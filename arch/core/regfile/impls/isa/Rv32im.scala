package arch.core.regfile.impls.isa.rv32im

import arch.core.regfile._
import arch.core.regfile.impls.isa.rv32i.RegfileRv32iIsa
import vutils.graph.{ NodeRegistry, RegisteredNodeUtils }

object RegfileRv32imIsa extends RegisteredNodeUtils[RegfileIsaImpl] {
  override def utils: RegfileIsaImpl = new RegfileIsaImpl {
    private val rv32i = RegfileRv32iIsa.utils

    override def value: String = "rv32im"

    override def initValue(addr: Int): BigInt = rv32i.initValue(addr)
    override def regName(addr: Int): String   = rv32i.regName(addr)
  }

  override def registry: NodeRegistry[RegfileIsaImpl] = RegfileIsaFactory
}
