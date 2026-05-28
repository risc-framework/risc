package arch.core.imm.riscv

import arch.core.imm._
import arch.configs._
import chisel3._

object RV32IMImmUtils extends RegisteredUtils[ImmUtils] {
  override def utils: ImmUtils = new ImmUtils {
    override def name: String                             = "rv32im"
    override def immTypeWidth: Int                        = RV32IImmUtils.utils.immTypeWidth
    override def genImm(instr: UInt, immType: UInt): UInt =
      RV32IImmUtils.utils.genImm(instr, immType)
    override def genCsrImm(instr: UInt): UInt             = RV32IImmUtils.utils.genCsrImm(instr)
  }

  override def factory: UtilsFactory[ImmUtils] = ImmUtilsFactory
}
