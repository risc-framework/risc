package arch.node.regfile.impls.isa.rv32im

import arch.configs._
import arch.node.regfile._
import arch.node.regfile.impls.isa.rv32i.RegfileRv32iIsa
import vutils.graph.{ NodeRegistry, RegisteredNodeUtils }
import chisel3._

object RegfileRv32imIsa extends RegisteredNodeUtils[RegfileIsaImpl] {
  override def utils: RegfileIsaImpl = new RegfileIsaImpl {
    private val rv32i = RegfileRv32iIsa.utils

    override def value: String = "rv32im"

    override def getRs1(instr: UInt): UInt = rv32i.getRs1(instr)
    override def getRs2(instr: UInt): UInt = rv32i.getRs2(instr)
    override def getRd(instr: UInt): UInt  = rv32i.getRd(instr)

    override def readable(addr: UInt)(implicit p: Parameters): Bool = rv32i.readable(addr)
    override def writable(addr: UInt)(implicit p: Parameters): Bool = rv32i.writable(addr)

    override def initValue(addr: Int): BigInt = rv32i.initValue(addr)
    override def regName(addr: Int): String   = rv32i.regName(addr)
  }

  override def registry: NodeRegistry[RegfileIsaImpl] = RegfileIsaFactory
}
