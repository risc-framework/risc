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
  val exception      = new FuPoolExceptionIO
  val scheduler      = new FuPoolSchedulerIO
  val rob            = new FuPoolRobIO
  val memory_arbiter = new FuPoolMemoryArbiterIO
  val sb             = new FuPoolSbIO
  val csr            = new VecCsrCtrlIO
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
    io.scheduler.reqs(i).ready := false.B
    io.scheduler.done(i).valid := false.B
    io.scheduler.done(i).bits  := 0.U.asTypeOf(new FuResp)

    io.rob.done(i).valid := false.B
    io.rob.done(i).bits  := 0.U.asTypeOf(new FuResp)
  }

  for (i <- 0 until p(NumBRUs))
    io.rob.bru(i).resolved := 0.U.asTypeOf(io.rob.bru(i).resolved)

  for (i <- 0 until p(NumLDs)) {
    io.memory_arbiter.load_mem(i).req.valid   := false.B
    io.memory_arbiter.load_mem(i).req.bits    := 0.U.asTypeOf(io.memory_arbiter.load_mem(i).req.bits)
    io.memory_arbiter.load_mem(i).resp.ready  := false.B
    io.memory_arbiter.load_mmio(i).req.valid  := false.B
    io.memory_arbiter.load_mmio(i).req.bits   := 0.U.asTypeOf(io.memory_arbiter.load_mmio(i).req.bits)
    io.memory_arbiter.load_mmio(i).resp.ready := false.B
    io.sb.fwd(i).req.valid                    := false.B
    io.sb.fwd(i).req.bits                     := 0.U.asTypeOf(io.sb.fwd(i).req.bits)
    io.sb.fwd(i).resp.ready                   := false.B
  }

  for (i <- 0 until p(NumSTs)) {
    io.sb.write(i).valid := false.B
    io.sb.write(i).bits  := 0.U.asTypeOf(io.sb.write(i).bits)
  }

  for (i <- 0 until io.csr.ports.length) {
    io.csr.ports(i).view := 0.U.asTypeOf(new CsrTrapView)
    io.csr.ports(i).busy := false.B
  }

  private val units = p(FunctionalUnits).zipWithIndex.map { case (desc, idx) =>
    build(desc) -> idx
  }

  private def connectFu(fu: FuIO, idx: Int): Unit = {
    fu.flush                     := io.exception.flush
    fu.req <> io.scheduler.reqs(idx)
    fu.resp.ready                := true.B
    io.scheduler.done(idx).valid := fu.resp.valid && !io.exception.flush
    io.scheduler.done(idx).bits  := fu.resp.bits
    io.rob.done(idx).valid       := fu.resp.valid && !io.exception.flush
    io.rob.done(idx).bits        := fu.resp.bits
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

        ld.io.mem.mem <> io.memory_arbiter.load_mem(ldIdx)
        ld.io.mem.mmio <> io.memory_arbiter.load_mmio(ldIdx)

        ld.io.sb.sb_fwd <> io.sb.fwd(ldIdx)
        ld.io.sb.oldest_valid := io.sb.oldest_valid
        ld.io.sb.oldest_seq   := io.sb.oldest_seq

        ldIdx += 1

      case st: St =>
        connectFu(st.io.fu, fuIdx)
        io.sb.write(stIdx) := st.io.sb.write
        stIdx += 1

      case bru: Bru =>
        connectFu(bru.io.fu, fuIdx)
        io.rob.bru(bruIdx) <> bru.io.resolve
        bruIdx += 1

      case csr: Csr =>
        connectFu(csr.io.fu, fuIdx)
        csr.io.ctrl <> io.csr.ports(csrIdx)
        csrIdx += 1

      case _ =>
    }
}
