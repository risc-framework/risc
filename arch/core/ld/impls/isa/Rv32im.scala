package arch.core.ld.impls.isa.rv32im

import arch.core.ld._
import arch.core.ld.impls.isa.rv32i.LdRv32iIsa
import arch.core.fupool.FuReq
import arch.configs._
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._

object LdRv32imIsa extends RegisteredNodeUtils[LdIsaImpl] {
  override def utils: LdIsaImpl = new LdIsaImpl {
    private val rv32i = LdRv32iIsa.utils

    override def value: String = "rv32im"

    override def addr(uop: FuReq)(implicit p: Parameters): UInt =
      rv32i.addr(uop)

    override def data(uop: FuReq)(implicit p: Parameters): UInt =
      rv32i.data(uop)

    override def mask(uop: FuReq)(implicit p: Parameters): UInt =
      rv32i.mask(uop)
  }

  override def registry: NodeDimensionRegistry[LdIsaImpl] =
    LdIsaFactory
}
