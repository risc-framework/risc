package arch.core.div

import arch.configs._
import chisel3._

trait DivUtils extends Utils {
  def decode(uop: UInt): DivCtrl
}

object DivUtilsFactory extends UtilsFactory[DivUtils]("Div")

object DivInit {
  val rv32iUtils  = riscv.RV32IDivUtils
  val rv32imUtils = riscv.RV32IMDivUtils
}
