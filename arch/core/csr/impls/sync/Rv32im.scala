package arch.core.csr.impls.sync.rv32im

import arch.core.csr._
import arch.core.csr.impls.sync.rv32i.Rv32iCsrSync
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }

object Rv32imCsrSync extends RegisteredNodeUtils[CsrSyncImpl] {
  override def utils: CsrSyncImpl = new CsrSyncImpl {
    private val rv32i = Rv32iCsrSync.utils

    override def value: String =
      "rv32im"

    override def command(instr: chisel3.UInt, uop: chisel3.UInt)(implicit
      p: arch.configs.Parameters
    ): CsrSyncCmd =
      rv32i.command(instr, uop)

    override def illegalAccessKind(cmd: CsrFileCmd)(implicit
      p: arch.configs.Parameters
    ): chisel3.UInt =
      rv32i.illegalAccessKind(cmd)

    override def view(regs: Map[String, chisel3.UInt], extra: Map[String, chisel3.UInt])(implicit
      p: arch.configs.Parameters
    ): CsrTrapView =
      rv32i.view(regs, extra)

    override def trapEntryUpdates(
      regs: Map[String, chisel3.UInt],
      update: arch.core.exception.ExceptionTrapUpdate
    )(implicit p: arch.configs.Parameters): Map[String, chisel3.UInt] =
      rv32i.trapEntryUpdates(regs, update)

    override def trapReturnTarget(regs: Map[String, chisel3.UInt])(implicit
      p: arch.configs.Parameters
    ): chisel3.UInt =
      rv32i.trapReturnTarget(regs)

    override def trapReturnUpdates(regs: Map[String, chisel3.UInt])(implicit
      p: arch.configs.Parameters
    ): Map[String, chisel3.UInt] =
      rv32i.trapReturnUpdates(regs)

    override def trapTarget(view: CsrTrapView)(implicit p: arch.configs.Parameters): chisel3.UInt =
      rv32i.trapTarget(view)
  }

  override def registry: NodeDimensionRegistry[CsrSyncImpl] =
    CsrSyncFactory
}
