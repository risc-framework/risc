package arch.core.ld.impls.isa.rv32im

import arch.configs._
import arch.core.ld._
import arch.core.ld.impls.isa.rv32i.LdRv32iIsa
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._

object LdRv32imIsa extends RegisteredNodeUtils[LdIsaImpl] {
  override def utils: LdIsaImpl = new LdIsaImpl {
    private val rv32i = LdRv32iIsa.utils

    override def value: String = "rv32im"

    override def decode(uop: UInt)(implicit p: Parameters): LoadCtrl =
      rv32i.decode(uop)
  }

  override def registry: NodeDimensionRegistry[LdIsaImpl] =
    LdIsaFactory
}
