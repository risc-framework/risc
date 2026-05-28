package arch.core.csr.riscv

import arch.core.csr._
import arch.core.regfile.Register
import arch.configs._
import chisel3._

object RV32IMCsrUtils extends RegisteredUtils[CsrUtils] {
  override def utils: CsrUtils = new CsrUtils {
    override def name: String               = "rv32im"
    override def addrWidth: Int             = RV32ICsrUtils.utils.addrWidth
    override def opWidth: Int               = RV32ICsrUtils.utils.opWidth
    override def getAddr(instr: UInt): UInt = RV32ICsrUtils.utils.getAddr(instr)
    override def genImm(imm: UInt): UInt    = RV32ICsrUtils.utils.genImm(imm)

    override def decode(uop: UInt): CsrCtrl                          = RV32ICsrUtils.utils.decode(uop)
    override def fn(cmd: UInt, csr_data: UInt, src_data: UInt): UInt =
      RV32ICsrUtils.utils.fn(cmd, csr_data, src_data)

    override def extraInputs: Seq[(String, Int)] = RV32ICsrUtils.utils.extraInputs

    override def table: Seq[(Register, CsrUpdateBehavior)]                                         = RV32ICsrUtils.utils.table.map {
      case (reg, behavior) if reg.name == "misa" =>
        val newMisaReg = Register(
          name = reg.name,
          addr = reg.addr,
          initValue = 0x40001100L,
          writable = reg.writable
        )
        (newMisaReg, behavior)

      case other => other
    }

    override def checkInterrupts(
      regs: Map[String, UInt],
      extra: Map[String, UInt]
    ): (Bool, UInt, UInt) =
      RV32ICsrUtils.utils.checkInterrupts(regs, extra)
    override def getTrapUpdates(regs: Map[String, UInt], pc: UInt, cause: UInt): Map[String, UInt] =
      RV32ICsrUtils.utils.getTrapUpdates(regs, pc, cause)
    override def getTrapReturnTarget(regs: Map[String, UInt]): UInt                                =
      RV32ICsrUtils.utils.getTrapReturnTarget(regs)
    override def getTrapReturnUpdates(regs: Map[String, UInt]): Map[String, UInt]                  =
      RV32ICsrUtils.utils.getTrapReturnUpdates(regs)
  }

  override def factory: UtilsFactory[CsrUtils] = CsrUtilsFactory
}
