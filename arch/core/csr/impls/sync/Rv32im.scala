package arch.core.csr.impls.sync.rv32im

import arch.configs._
import arch.core.csr._
import arch.core.csr.impls.sync.rv32i.CsrRv32iSync
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._

object CsrRv32imSync extends RegisteredNodeUtils[CsrSyncImpl] {
  override def utils: CsrSyncImpl = new CsrSyncImpl {
    private val rv32i = CsrRv32iSync.utils

    override def value: String = "rv32im"

    override def command(instr: UInt, uop: UInt)(implicit p: Parameters): CsrSyncCmd =
      rv32i.command(instr, uop)

    override def illegalAccessCause(cmd: CsrFileCmd)(implicit p: Parameters): UInt =
      rv32i.illegalAccessCause(cmd)

    override def view(regs: Map[String, UInt], extra: Map[String, UInt])(implicit
      p: Parameters
    ): CsrTrapView =
      rv32i.view(regs, extra)

    override def trapEntryUpdates(regs: Map[String, UInt], update: CsrTrapUpdate)(implicit
      p: Parameters
    ): Map[String, UInt] =
      rv32i.trapEntryUpdates(regs, update)

    override def trapReturnTarget(regs: Map[String, UInt])(implicit p: Parameters): UInt =
      rv32i.trapReturnTarget(regs)

    override def trapReturnUpdates(regs: Map[String, UInt])(implicit
      p: Parameters
    ): Map[String, UInt] =
      rv32i.trapReturnUpdates(regs)

    override def trapTarget(view: CsrTrapView)(implicit p: Parameters): UInt =
      rv32i.trapTarget(view)
  }

  override def registry: NodeDimensionRegistry[CsrSyncImpl] =
    CsrSyncFactory
}
