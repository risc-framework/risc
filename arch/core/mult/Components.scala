package arch.core.mult

import arch.configs._
import chisel3._

trait MultUtils extends Utils {
  def decode(uop: UInt): MultCtrl
}

object MultUtilsFactory extends UtilsFactory[MultUtils]("Mult")

object MultInit {
  val rv32iUtils  = riscv.RV32IMultUtils
  val rv32imUtils = riscv.RV32IMMultUtils
}
