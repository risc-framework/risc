package arch.core.alu.impls.isa.rv32im

import arch.core.alu.impls.isa.rv32i.AluRv32iIsa
import arch.core.uop.MicroOp
import arch.core.alu._
import arch.configs._
import vutils.graph.{ NodeRegistry, RegisteredNodeUtils }
import chisel3._

object AluRv32imIsa extends RegisteredNodeUtils[AluIsaImpl] {
  override def utils: AluIsaImpl = new AluIsaImpl {
    private val rv32i = AluRv32iIsa.utils

    override def value: String                                       = "rv32im"
    override def fnTypeWidth: Int                                    = rv32i.fnTypeWidth
    override def decode(uop: UInt): AluCtrl                          = rv32i.decode(uop)
    override def execute(uop: MicroOp)(implicit p: Parameters): UInt = rv32i.execute(uop)
  }

  override def registry: NodeRegistry[AluIsaImpl] = AluIsaFactory
}
