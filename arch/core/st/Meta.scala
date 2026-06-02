package arch.core.st

import arch.core.fupool.FuIO
import arch.configs._
import vutils.graph.{ NodeDim, NodeDimensionImpl, NodeDimensionRegistry, NodePort, NodeType }
import chisel3._

object StMeta {
  val Type = NodeType("st")
  val FU   = NodePort[StIO, FuIO]("fu", _.fu)
  val SB   = NodePort[StIO, StSbWriteIO]("sb", _.sb)
}

object StDims {
  val ISA = NodeDim("isa")
}

trait StIsaImpl extends NodeDimensionImpl with StoreDataHelpers {
  override def nodeType: NodeType = StMeta.Type
  override def dim: NodeDim       = StDims.ISA
  override def name: String       = value

  def decodeStore(uop: UInt)(implicit p: Parameters): StoreCtrl
}

object StIsaFactory extends NodeDimensionRegistry[StIsaImpl](StMeta.Type, StDims.ISA)

object StInit {
  val rv32i  = impls.isa.rv32i.StRv32iIsa
  val rv32im = impls.isa.rv32im.StRv32imIsa
}
