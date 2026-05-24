package arch.core.div.riscv

import arch.core.div._
import arch.configs._
import chisel3._

object RV32IDivUtils extends RegisteredUtils[DivUtils] {
  override def utils: DivUtils = new DivUtils {
    override def name: String = "rv32i"

    override def decode(uop: UInt): DivCtrl = {
      val ctrl = Wire(new DivCtrl)
      ctrl.is_signed := false.B
      ctrl.is_rem    := false.B
      ctrl
    }

  }

  override def factory: UtilsFactory[DivUtils] = DivUtilsFactory
}
