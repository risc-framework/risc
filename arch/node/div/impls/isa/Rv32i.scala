package arch.node.div.impls.isa.rv32i

import arch.node.div._
import vutils.graph.{ NodeRegistry, RegisteredNodeUtils }
import chisel3._

object DivRv32iIsa extends RegisteredNodeUtils[DivIsaImpl] {
  override def utils: DivIsaImpl = new DivIsaImpl {
    override def value: String = "rv32i"

    override def decode(uop: UInt): DivCtrl = {
      val ctrl = Wire(new DivCtrl)
      ctrl.is_signed := false.B
      ctrl.is_rem    := false.B
      ctrl
    }
  }

  override def registry: NodeRegistry[DivIsaImpl] = DivIsaFactory
}
