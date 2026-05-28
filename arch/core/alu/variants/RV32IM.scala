package arch.core.alu.riscv

import arch.core.alu._
import arch.configs._
import chisel3._

object RV32IMAluUtils extends RegisteredUtils[AluUtils] {
  override def utils: AluUtils = new AluUtils {
    override def name: String                                               = "rv32im"
    override def sel1Width: Int                                             = RV32IAluUtils.utils.sel1Width
    override def sel2Width: Int                                             = RV32IAluUtils.utils.sel2Width
    override def fnTypeWidth: Int                                           = RV32IAluUtils.utils.fnTypeWidth
    override def decode(uop: UInt): AluCtrl                                 = RV32IAluUtils.utils.decode(uop)
    override def fn(src1: UInt, src2: UInt, fnType: UInt, mode: Bool): UInt =
      RV32IAluUtils.utils.fn(src1, src2, fnType, mode)
  }

  override def factory: UtilsFactory[AluUtils] = AluUtilsFactory
}
