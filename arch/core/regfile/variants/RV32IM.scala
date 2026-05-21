package arch.core.regfile.riscv

import arch.core.regfile._
import arch.configs._
import chisel3._

object RV32IMRegfileUtils extends RegisteredUtils[RegfileUtils] {
  override def utils: RegfileUtils = new RegfileUtils {
    override def name: String               = "rv32im"
    override def getRs1(instr: UInt): UInt  = RV32IRegfileUtils.utils.getRs1(instr)
    override def getRs2(instr: UInt): UInt  = RV32IRegfileUtils.utils.getRs2(instr)
    override def getRd(instr: UInt): UInt   = RV32IRegfileUtils.utils.getRd(instr)
    override def extraInfo: Seq[Register]   = RV32IRegfileUtils.utils.extraInfo
    override def writable(addr: UInt): Bool = RV32IRegfileUtils.utils.writable(addr)
    override def readable(addr: UInt): Bool = RV32IRegfileUtils.utils.readable(addr)
  }

  override def factory: UtilsFactory[RegfileUtils] = RegfileUtilsFactory
}
