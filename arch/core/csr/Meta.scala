package arch.core.csr

import arch.configs._
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

  def command(
    instr: UInt,
    uop: UInt,
    rs1: UInt,
    rd: UInt,
    rs1Data: UInt,
    imm: UInt
  )(implicit p: Parameters): CsrFileCmd

  def write(old: UInt, cmd: CsrFileCmd)(implicit p: Parameters): UInt
}

trait CsrSyncImpl extends CsrDims.SYNC.Impl {
  def command(instr: UInt, uop: UInt)(implicit p: Parameters): CsrSyncCmd

  def illegalAccessCause(cmd: CsrFileCmd)(implicit p: Parameters): UInt

  def view(
    regs: Map[String, UInt],
    extra: Map[String, UInt]
  )(implicit p: Parameters): CsrTrapView = {
    val out = Wire(new CsrTrapView)
    out := 0.U.asTypeOf(new CsrTrapView)
    out
  }

  def trapEntryUpdates(regs: Map[String, UInt], update: CsrTrapUpdate)(implicit
    p: Parameters
  ): Map[String, UInt] =
    Map.empty[String, UInt]

  def trapReturnTarget(regs: Map[String, UInt])(implicit p: Parameters): UInt =
    0.U(p(XLen).W)

  def trapReturnUpdates(regs: Map[String, UInt])(implicit p: Parameters): Map[String, UInt] =
    Map.empty[String, UInt]

  def trapTarget(view: CsrTrapView)(implicit p: Parameters): UInt =
    view.trapVector
}

trait CsrIrImpl extends CsrDims.IR.Impl {
  def command(regs: Map[String, UInt], extra: Map[String, UInt])(implicit p: Parameters): CsrIrCmd
}

object CsrFileFactory extends CsrDims.FILE.Registry[CsrFileImpl]
object CsrSyncFactory extends CsrDims.SYNC.Registry[CsrSyncImpl]
object CsrIrFactory   extends CsrDims.IR.Registry[CsrIrImpl]

object CsrInit {
  val rv32iFile  = impls.file.rv32i.CsrRv32iFile.registered
  val rv32imFile = impls.file.rv32im.CsrRv32imFile.registered

  val rv32iSync  = impls.sync.rv32i.CsrRv32iSync.registered
  val rv32imSync = impls.sync.rv32im.CsrRv32imSync.registered

  val rv32iIr  = impls.ir.rv32i.CsrRv32iIr.registered
  val rv32imIr = impls.ir.rv32im.CsrRv32imIr.registered
}
