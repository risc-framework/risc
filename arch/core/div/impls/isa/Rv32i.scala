package arch.core.div.impls.isa.rv32i

import arch.core.div._
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
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

  override def registry: NodeDimensionRegistry[DivIsaImpl] =
    DivIsaFactory
}
