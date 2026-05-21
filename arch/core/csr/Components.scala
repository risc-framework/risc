package arch.core.csr

import arch.core.regfile.Register
import arch.configs._
import chisel3._

object CsrUpdateBehavior {
  type CsrUpdateFn = Map[String, UInt] => UInt
}

sealed trait CsrUpdateBehavior
case object NormalUpdate                                        extends CsrUpdateBehavior
case class AlwaysUpdate(fn: CsrUpdateBehavior.CsrUpdateFn)      extends CsrUpdateBehavior
case class ConditionalUpdate(fn: CsrUpdateBehavior.CsrUpdateFn) extends CsrUpdateBehavior

trait CsrUtils extends Utils {
  def addrWidth: Int
  def opWidth: Int

  def getAddr(instr: UInt): UInt
  def genImm(instr: UInt): UInt

  def decode(uop: UInt): CsrCtrl
  def fn(op: UInt, csr_data: UInt, src_data: UInt): UInt

  def table: Seq[(Register, CsrUpdateBehavior)]
  def extraInputs: Seq[(String, Int)]

  // Trap Interrupt Interface
  def checkInterrupts(regs: Map[String, UInt], extra: Map[String, UInt]): (Bool, UInt, UInt) =
    (false.B, 0.U, 0.U)
  def getTrapUpdates(regs: Map[String, UInt], pc: UInt, cause: UInt): Map[String, UInt]      =
    Map.empty[String, UInt]

  // Trap Return Interface
  def getTrapReturnTarget(regs: Map[String, UInt]): UInt               = 0.U
  def getTrapReturnUpdates(regs: Map[String, UInt]): Map[String, UInt] = Map.empty
}

object CsrUtilsFactory extends UtilsFactory[CsrUtils]("CSR")

object CsrInit {
  val rv32iUtils  = riscv.RV32ICsrUtils
  val rv32imUtils = riscv.RV32IMCsrUtils
}
