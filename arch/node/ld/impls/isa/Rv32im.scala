package arch.node.ld.impls.isa.rv32im

import arch.configs._
import arch.node.ld._
import arch.node.ld.impls.isa.rv32i.LdRv32iIsa
import vutils.graph.{ NodeRegistry, RegisteredNodeUtils }
import chisel3._

object LdRv32imIsa extends RegisteredNodeUtils[LdIsaImpl] {
  override def utils: LdIsaImpl = new LdIsaImpl {
    private val rv32i = LdRv32iIsa.utils

    override def value: String = "rv32im"

    override def decodeLoad(uop: UInt)(implicit p: Parameters): LoadCtrl =
      rv32i.decodeLoad(uop)
  }

  override def registry: NodeRegistry[LdIsaImpl] = LdIsaFactory
}
