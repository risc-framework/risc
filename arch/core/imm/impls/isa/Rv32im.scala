package arch.core.imm.impls.isa.rv32im

import arch.core.imm.{ ImmIsaImpl, ImmIsaFactory }
import arch.core.imm.impls.isa.rv32i.ImmRv32iIsa
import vutils.graph.{ RegisteredNodeUtils, NodeRegistry }
import chisel3._

object ImmRv32imIsa extends RegisteredNodeUtils[ImmIsaImpl] {
  override def utils: ImmIsaImpl = new ImmIsaImpl {
    private val rv32i = ImmRv32iIsa.utils

    override def value: String                         = "rv32im"
    override def immTypeWidth: Int                     = rv32i.immTypeWidth
    override def gen(instr: UInt, immType: UInt): UInt = rv32i.gen(instr, immType)
  }

  override def registry: NodeRegistry[ImmIsaImpl] = ImmIsaFactory
}
