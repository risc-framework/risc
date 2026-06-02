package arch.node.csr.impls.isa.rv32im

import arch.configs._
import arch.core.regfile.Register
import arch.node.csr._
import arch.node.csr.impls.isa.rv32i.CsrRv32iIsa
import chisel3._
import vutils.graph.{ NodeRegistry, RegisteredNodeUtils }

object CsrRv32imIsa extends RegisteredNodeUtils[CsrIsaImpl] {
  override def utils: CsrIsaImpl = new CsrIsaImpl {
    private val rv32i = CsrRv32iIsa.utils

    override def value: String  = "rv32im"
    override def addrWidth: Int = rv32i.addrWidth
    override def opWidth: Int   = rv32i.opWidth

    override def getAddr(instr: UInt)(implicit p: Parameters): UInt = rv32i.getAddr(instr)
    override def genImm(instr: UInt)(implicit p: Parameters): UInt  = rv32i.genImm(instr)
    override def decode(uop: UInt): CsrCtrl                         = rv32i.decode(uop)

    override def fn(op: UInt, csrData: UInt, srcData: UInt)(implicit p: Parameters): UInt =
      rv32i.fn(op, csrData, srcData)

    override def table(implicit p: Parameters): Seq[(Register, CsrUpdateBehavior)] =
      rv32i.table.map {
        case (reg, behavior) if reg.name == "misa" =>
          (Register(reg.name, reg.addr, 0x40001100L, reg.writable), behavior)
        case other                                 => other
      }

    override def view(regs: Map[String, UInt], extra: Map[String, UInt])(implicit
      p: Parameters
    ): CsrTrapView =
      rv32i.view(regs, extra)

    override def trapEntryUpdates(regs: Map[String, UInt], pc: UInt, cause: UInt)(implicit
      p: Parameters
    ): Map[String, UInt] =
      rv32i.trapEntryUpdates(regs, pc, cause)

    override def trapReturnTarget(regs: Map[String, UInt])(implicit p: Parameters): UInt =
      rv32i.trapReturnTarget(regs)

    override def trapReturnUpdates(regs: Map[String, UInt])(implicit
      p: Parameters
    ): Map[String, UInt] =
      rv32i.trapReturnUpdates(regs)

    override def isTrapReturn(instr: UInt, uop: UInt)(implicit p: Parameters): Bool =
      rv32i.isTrapReturn(instr, uop)

    override def hasSyncException(instr: UInt, uop: UInt)(implicit p: Parameters): Bool =
      rv32i.hasSyncException(instr, uop)

    override def syncExceptionCause(instr: UInt, uop: UInt)(implicit p: Parameters): UInt =
      rv32i.syncExceptionCause(instr, uop)
  }

  override def registry: NodeRegistry[CsrIsaImpl] = CsrIsaFactory
}
