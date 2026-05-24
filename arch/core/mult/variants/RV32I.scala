package arch.core.mult.riscv

import arch.core.mult._
import arch.configs._
import chisel3._

object RV32IMultUtils extends RegisteredUtils[MultUtils] {
  override def utils: MultUtils = new MultUtils {
    override def name: String = "rv32i"

    override def decode(uop: UInt): MultCtrl = 0.U.asTypeOf(new MultCtrl)
  }

  override def factory: UtilsFactory[MultUtils] = MultUtilsFactory
}
