package arch.core.csr

import arch.configs._
import chisel3._
import vutils.graph.{ NodeDimensionRegistry, NodeDims }

object CsrDims extends NodeDims("csr") {
  val ISA = dim("isa")
}

trait CsrIsaImpl extends CsrDims.ISA.Impl {
  def addrWidth: Int
  def opWidth: Int

  def getAddr(instr: UInt)(implicit p: Parameters): UInt
  def decode(uop: UInt): CsrCtrl
  def fn(op: UInt, csrData: UInt, srcData: UInt)(implicit p: Parameters): UInt
  def table(implicit p: Parameters): Seq[(CsrRegister, CsrUpdateBehavior)]

  def view(
    regs: Map[String, UInt],
    extra: Map[String, UInt]
  )(implicit p: Parameters): CsrTrapView = {
    val out = Wire(new CsrTrapView)
    out := 0.U.asTypeOf(new CsrTrapView)
    out
  }

  def trapEntryUpdates(
    regs: Map[String, UInt],
    pc: UInt,
    cause: UInt
  )(implicit p: Parameters): Map[String, UInt] =
    Map.empty[String, UInt]

  def trapReturnTarget(regs: Map[String, UInt])(implicit p: Parameters): UInt =
    0.U(p(XLen).W)

  def trapReturnUpdates(regs: Map[String, UInt])(implicit p: Parameters): Map[String, UInt] =
    Map.empty[String, UInt]

  def isTrapReturn(instr: UInt, uop: UInt)(implicit p: Parameters): Bool =
    false.B

  def hasSyncException(instr: UInt, uop: UInt)(implicit p: Parameters): Bool =
    false.B

  def syncExceptionCause(instr: UInt, uop: UInt)(implicit p: Parameters): UInt =
    0.U(p(XLen).W)

  def trapTarget(view: CsrTrapView): UInt =
    view.trapVector
}

object CsrIsaFactory extends CsrDims.ISA.Registry[CsrIsaImpl]

object CsrInit {
  val rv32i  = impls.isa.rv32i.CsrRv32iIsa.registered
  val rv32im = impls.isa.rv32im.CsrRv32imIsa.registered
}
