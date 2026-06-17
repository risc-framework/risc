package arch.core.csr

import arch.configs._
import arch.core.exception.ExceptionTrapUpdate
import vutils.graph.NodeDims
import chisel3._

object CsrDims extends NodeDims("csr") {
  val FILE = dim("file")
  val SYNC = dim("sync")
  val IR   = dim("ir")
}

trait CsrFileImpl extends CsrDims.FILE.Impl {
  def addrWidth: Int
  def opWidth: Int
  def table(implicit p: Parameters): Seq[(CsrRegister, CsrUpdateBehavior)]
  def command(instr: UInt, uop: UInt, rs1: UInt, rd: UInt, rs1Data: UInt, imm: UInt)(implicit
    p: Parameters
  ): CsrFileCmd
  def write(old: UInt, cmd: CsrFileCmd)(implicit p: Parameters): UInt
}

trait CsrSyncImpl extends CsrDims.SYNC.Impl {
  def command(instr: UInt, uop: UInt, view: CsrTrapView)(implicit p: Parameters): CsrSysCmd
  def illegalAccessKind(cmd: CsrFileCmd)(implicit p: Parameters): UInt

  def view(
    regs: Map[String, UInt],
    extra: Map[String, UInt]
  )(implicit p: Parameters): CsrTrapView = {
    val out = Wire(new CsrTrapView)
    out := 0.U.asTypeOf(new CsrTrapView)
    out
  }

  def trapUpdates(regs: Map[String, UInt], update: ExceptionTrapUpdate)(implicit
    p: Parameters
  ): Map[String, UInt] =
    Map.empty[String, UInt]

  def trapTarget(view: CsrTrapView)(implicit p: Parameters): UInt =
    view.trapVector
}

trait CsrIrImpl extends CsrDims.IR.Impl {
  def command(regs: Map[String, UInt], extra: Map[String, UInt])(implicit p: Parameters): CsrSysCmd
}

object CsrFileFactory extends CsrDims.FILE.Registry[CsrFileImpl]
object CsrSyncFactory extends CsrDims.SYNC.Registry[CsrSyncImpl]
object CsrIrFactory   extends CsrDims.IR.Registry[CsrIrImpl]

object CsrInit {
  val rv32iFile  = impls.file.rv32i.Rv32iCsrFile.registered
  val rv32imFile = impls.file.rv32im.Rv32imCsrFile.registered
  val rv32iSync  = impls.sync.rv32i.Rv32iCsrSync.registered
  val rv32imSync = impls.sync.rv32im.Rv32imCsrSync.registered
  val rv32iIr    = impls.ir.rv32i.Rv32iCsrIr.registered
  val rv32imIr   = impls.ir.rv32im.Rv32imCsrIr.registered
}
