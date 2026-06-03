package arch.core.regfile.impls.isa.rv32i

import arch.core.regfile._
import vutils.graph.{ NodeRegistry, RegisteredNodeUtils }

object RegfileRv32iIsa extends RegisteredNodeUtils[RegfileIsaImpl] {
  override def utils: RegfileIsaImpl = new RegfileIsaImpl {
    override def value: String = "rv32i"

    override def regName(addr: Int): String =
      addr match {
        case 0  => "zero"
        case 1  => "ra"
        case 2  => "sp"
        case 3  => "gp"
        case 4  => "tp"
        case 5  => "t0"
        case 6  => "t1"
        case 7  => "t2"
        case 8  => "s0"
        case 9  => "s1"
        case 10 => "a0"
        case 11 => "a1"
        case 12 => "a2"
        case 13 => "a3"
        case 14 => "a4"
        case 15 => "a5"
        case 16 => "a6"
        case 17 => "a7"
        case 18 => "s2"
        case 19 => "s3"
        case 20 => "s4"
        case 21 => "s5"
        case 22 => "s6"
        case 23 => "s7"
        case 24 => "s8"
        case 25 => "s9"
        case 26 => "s10"
        case 27 => "s11"
        case 28 => "t3"
        case 29 => "t4"
        case 30 => "t5"
        case 31 => "t6"
        case _  => s"x$addr"
      }
  }

  override def registry: NodeRegistry[RegfileIsaImpl] = RegfileIsaFactory
}
