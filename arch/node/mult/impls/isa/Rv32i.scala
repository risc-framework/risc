package arch.node.mult.impls.isa.rv32i

import arch.node.mult._
import vutils.graph.{ NodeRegistry, RegisteredNodeUtils }
import chisel3._

object MultRv32iIsa extends RegisteredNodeUtils[MultIsaImpl] {
  override def utils: MultIsaImpl = new MultIsaImpl {
    override def value: String = "rv32i"

    override def decode(uop: UInt): MultCtrl =
      0.U.asTypeOf(new MultCtrl)
  }

  override def registry: NodeRegistry[MultIsaImpl] = MultIsaFactory
}
