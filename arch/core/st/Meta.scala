package arch.core.st

import arch.configs._
import arch.core.fupool.FuReq
import vutils.graph.NodeDims
import chisel3._

object StDims extends NodeDims("st") {
  val ISA = dim("isa")
}

trait StIsaImpl extends StDims.ISA.Impl {
  def data(uop: FuReq)(implicit p: Parameters): UInt
  def addr(uop: FuReq)(implicit p: Parameters): UInt
  def mask(uop: FuReq)(implicit p: Parameters): UInt
}

object StIsaFactory extends StDims.ISA.Registry[StIsaImpl]

object StInit {
  val rv32i  = impls.isa.rv32i.StRv32iIsa.registered
  val rv32im = impls.isa.rv32im.StRv32imIsa.registered
}
