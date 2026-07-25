package arch.core.st.impls.isa.rv32im

import arch.configs._
import arch.core.st._
import arch.core.fupool.FuReq
import arch.core.st.impls.isa.rv32i.StRv32iIsa
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._

object StRv32imIsa extends RegisteredNodeUtils[StIsaImpl] {
  override def utils: StIsaImpl = new StIsaImpl {
    private val rv32i = StRv32iIsa.utils

    override def value: String = "rv32im"

    override def addr(uop: FuReq)(implicit p: Parameters): UInt =
      rv32i.addr(uop)

    override def data(uop: FuReq)(implicit p: Parameters): UInt =
      rv32i.data(uop)

    override def mask(uop: FuReq)(implicit p: Parameters): UInt =
      rv32i.mask(uop)
  }

  override def registry: NodeDimensionRegistry[StIsaImpl] =
    StIsaFactory
}
