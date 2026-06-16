package arch.core.csr.impls.sync.rv32im

import arch.configs._
import arch.core.csr._
import arch.core.csr.impls.sync.rv32i.Rv32iCsrSync
import arch.core.exception.ExceptionTrapUpdate
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._

object Rv32imCsrSync extends RegisteredNodeUtils[CsrSyncImpl] {
  override def utils: CsrSyncImpl = new CsrSyncImpl {
    private val rv32i = Rv32iCsrSync.utils

    override def value: String =
      "rv32im"

    override def command(instr: UInt, uop: UInt, view: CsrTrapView)(implicit
      p: Parameters
    ): CsrSyncCmd =
      rv32i.command(instr, uop, view)

    override def illegalAccessKind(cmd: CsrFileCmd)(implicit p: Parameters): UInt =
      rv32i.illegalAccessKind(cmd)

    override def view(regs: Map[String, UInt], extra: Map[String, UInt])(implicit
      p: Parameters
    ): CsrTrapView =
      rv32i.view(regs, extra)

    override def trapUpdates(regs: Map[String, UInt], update: ExceptionTrapUpdate)(implicit
      p: Parameters
    ): Map[String, UInt] =
      rv32i.trapUpdates(regs, update)

    override def trapTarget(view: CsrTrapView)(implicit p: Parameters): UInt =
      rv32i.trapTarget(view)
  }

  override def registry: NodeDimensionRegistry[CsrSyncImpl] =
    CsrSyncFactory
}
