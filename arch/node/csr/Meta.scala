package arch.node.csr

import arch.configs._
import arch.core.regfile.Register
import vutils.graph.{ NodeDim, NodeDimensionImpl, NodeDimensionRegistry, NodePort, NodeType }
import chisel3._

object CsrMeta {
  val Type = NodeType("csr")
  val FU   = NodePort[CsrIO, arch.node.fupool.FuIO]("fu", _.fu)
  val CTRL = NodePort[CsrIO, CsrCtrlIO]("ctrl", _.ctrl)
}

object CsrDims {
  val ISA = NodeDim("isa")
}

trait CsrIsaImpl extends NodeDimensionImpl {
  override def nodeType: NodeType = CsrMeta.Type
  override def dim: NodeDim       = CsrDims.ISA
  override def name: String       = value

  def addrWidth: Int
  def opWidth: Int

  def getAddr(instr: UInt)(implicit p: Parameters): UInt
  def genImm(instr: UInt)(implicit p: Parameters): UInt
  def decode(uop: UInt): CsrCtrl
  def fn(op: UInt, csrData: UInt, srcData: UInt)(implicit p: Parameters): UInt
  def table(implicit p: Parameters): Seq[(Register, CsrUpdateBehavior)]

  def view(
    regs: Map[String, UInt],
    extra: Map[String, UInt]
  )(implicit p: Parameters): CsrTrapView = {
    val out = Wire(new CsrTrapView)
    out := 0.U.asTypeOf(new CsrTrapView)
    out
  }

  def trapEntryUpdates(regs: Map[String, UInt], pc: UInt, cause: UInt): Map[String, UInt] =
    Map.empty[String, UInt]

  def trapReturnTarget(regs: Map[String, UInt])(implicit p: Parameters): UInt =
    0.U(p(XLen).W)

  def trapReturnUpdates(regs: Map[String, UInt]): Map[String, UInt] =
    Map.empty[String, UInt]

  def isTrapReturn(instr: UInt, uop: UInt): Bool =
    false.B

  def hasSyncException(instr: UInt, uop: UInt): Bool =
    false.B

  def syncExceptionCause(instr: UInt, uop: UInt)(implicit p: Parameters): UInt =
    0.U(p(XLen).W)

  def trapTarget(view: CsrTrapView): UInt =
    view.trapVector
}

object CsrIsaFactory extends NodeDimensionRegistry[CsrIsaImpl](CsrMeta.Type, CsrDims.ISA)

object CsrInit {
  val rv32i  = impls.isa.rv32i.CsrRv32iIsa
  val rv32im = impls.isa.rv32im.CsrRv32imIsa
}
