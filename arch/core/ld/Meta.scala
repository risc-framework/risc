package arch.core.ld

import arch.configs._
import vutils.graph.NodeDims
import chisel3._

object LdDims extends NodeDims("ld") {
  val ISA = dim("isa")
}

trait LdIsaImpl extends LdDims.ISA.Impl with LoadDataHelpers {
  def decodeLoad(uop: UInt)(implicit p: Parameters): LoadCtrl
}

object LdIsaFactory extends LdDims.ISA.Registry[LdIsaImpl]

object LdInit {
  val rv32i  = impls.isa.rv32i.LdRv32iIsa.registered
  val rv32im = impls.isa.rv32im.LdRv32imIsa.registered
}
