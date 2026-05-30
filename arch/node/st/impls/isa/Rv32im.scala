package arch.node.st.impls.isa.rv32im

import arch.configs._
import arch.node.st._
import arch.node.st.impls.isa.rv32i.StRv32iIsa
import vutils.graph.{ NodeRegistry, RegisteredNodeUtils }
import chisel3._

object StRv32imIsa extends RegisteredNodeUtils[StIsaImpl] {
  override def utils: StIsaImpl = new StIsaImpl {
    private val rv32i = StRv32iIsa.utils

    override def value: String = "rv32im"

    override def decodeStore(uop: UInt)(implicit p: Parameters): StoreCtrl =
      rv32i.decodeStore(uop)
  }

  override def registry: NodeRegistry[StIsaImpl] = StIsaFactory
}
