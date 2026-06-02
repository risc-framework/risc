package arch.core.regfile

import arch.configs._
import vutils.graph.{ NodeType, NodeDim, NodeDimensionImpl, NodeDimensionRegistry, NodePort }
import chisel3._

object RegfileMeta {
  val Type   = NodeType("regfile")
  val DECODE = NodePort[RegfileIO, RegfileDecodeIO]("decode", _.decode)
  val READ   = NodePort[RegfileIO, RegfileReadIO]("read", _.read)
  val WRITE  = NodePort[RegfileIO, RegfileWriteIO]("write", _.write)
  val DEBUG  = NodePort[RegfileIO, RegfileDebugIO]("debug", _.debug)
}

object RegfileDims {
  val ISA = NodeDim("isa")
}

trait RegfileIsaImpl extends NodeDimensionImpl {
  override def nodeType: NodeType = RegfileMeta.Type
  override def dim: NodeDim       = RegfileDims.ISA
  override def name: String       = value

  def getRs1(instr: UInt): UInt
  def getRs2(instr: UInt): UInt
  def getRd(instr: UInt): UInt

  def readable(addr: UInt)(implicit p: Parameters): Bool
  def writable(addr: UInt)(implicit p: Parameters): Bool

  def initValue(addr: Int): BigInt = 0
  def regName(addr: Int): String   = s"x$addr"
}

object RegfileIsaFactory
    extends NodeDimensionRegistry[RegfileIsaImpl](RegfileMeta.Type, RegfileDims.ISA)

object RegfileInit {
  val rv32i  = impls.isa.rv32i.RegfileRv32iIsa
  val rv32im = impls.isa.rv32im.RegfileRv32imIsa
}
