package arch.core.fupool

import arch.core.alu.Alu
import arch.core.div.Div
import arch.core.mult.Mult
import arch.core.ld.Ld
import arch.core.st.St
import arch.core.bru.Bru
import arch.core.csr.{ Csr, CsrTrapView }
import arch.core.fu.FunctionalUnitType
import arch.configs._
import vutils.graph.{ Node, NodeType }
import chisel3._

class FuPoolIO(implicit p: Parameters) extends Bundle {
  val fu     = new FuPoolFuIO
  val ld_mem = new VecLdMemIO
  val ld_sb  = new VecLdSbFwdIO
  val st_sb  = new VecStSbWriteIO
  val bru    = new VecBruResolveIO
  val csr    = new VecCsrCtrlIO
}

class FuPool(implicit p: Parameters) extends Node(new FuPoolIO) {
  override def nodeType: NodeType  = FuPoolMeta.Type
  override def desiredName: String = "fu_pool"

  private def build(desc: arch.core.fu.FunctionalUnitDescriptor): Node[_ <: Bundle] =
    desc.`type` match {
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ALU  => Module(new Alu)
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_MULT => Module(new Mult)
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_DIV  => Module(new Div)
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD   => Module(new Ld)
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST   => Module(new St)
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_BRU  => Module(new Bru)
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR  => Module(new Csr)
      case other                                        =>
        throw new UnsupportedOperationException(
          s"FuPool: unsupported FU type '${other.cppName}' for '${desc.name}'"
        )
    }

  for (i <- 0 until p(NumFUs)) {
    io.fu.req(i).ready  := false.B
    io.fu.done(i).valid := false.B
    io.fu.done(i).bits  := 0.U.asTypeOf(new FuResp)
  }

  for (i <- 0 until io.csr.ports.length) {
    io.csr.ports(i).view := 0.U.asTypeOf(new CsrTrapView)
    io.csr.ports(i).busy := false.B
  }

  private val units = p(FunctionalUnits).zipWithIndex.map { case (desc, idx) =>
    build(desc) -> idx
  }

  private def connectFu(fu: FuIO, idx: Int): Unit = {
    fu.flush      := io.fu.flush
    fu.req <> io.fu.req(idx)
    fu.resp.ready := true.B

    io.fu.done(idx).valid := fu.resp.valid && !io.fu.flush
    io.fu.done(idx).bits  := fu.resp.bits
  }

  private var ldIdx  = 0
  private var stIdx  = 0
  private var bruIdx = 0
  private var csrIdx = 0

  for ((unit, fuIdx) <- units)
    unit match {
      case alu: Alu =>
        connectFu(alu.io.fu, fuIdx)

      case mult: Mult =>
        connectFu(mult.io.fu, fuIdx)

      case div: Div =>
        connectFu(div.io.fu, fuIdx)

      case ld: Ld =>
        connectFu(ld.io.fu, fuIdx)
        ld.io.mem <> io.ld_mem.ports(ldIdx)
        ld.io.sb <> io.ld_sb.ports(ldIdx)
        ldIdx += 1

      case st: St =>
        connectFu(st.io.fu, fuIdx)
        st.io.sb <> io.st_sb.ports(stIdx)
        stIdx += 1

      case bru: Bru =>
        connectFu(bru.io.fu, fuIdx)
        io.bru.ports(bruIdx).resolved := bru.io.resolve.resolved
        bruIdx += 1

      case csr: Csr =>
        connectFu(csr.io.fu, fuIdx)
        csr.io.ctrl <> io.csr.ports(csrIdx)
        csrIdx += 1

      case _ =>
    }
}
