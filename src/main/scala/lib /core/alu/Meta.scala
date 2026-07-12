package arch.core.alu

import arch.core.fupool.FuReq
import arch.configs._
import vutils.graph.NodeDims
import chisel3._

object AluDims extends NodeDims("alu") {
  val ISA = dim("isa")
}

trait AluIsaImpl extends AluDims.ISA.Impl {
  def execute(uop: FuReq)(implicit p: Parameters): UInt
}

object AluIsaFactory extends AluDims.ISA.Registry[AluIsaImpl]

object AluInit {
  val rv32i  = impls.isa.rv32i.AluRv32iIsa.registered
  val rv32im = impls.isa.rv32im.AluRv32imIsa.registered
}
