package arch.core.fupool

import arch.configs._
import arch.core.alu.Alu
import arch.core.bru.Bru
import arch.core.csr.{ Csr, CsrTrapView }
import arch.core.div.Div
import arch.core.ld.Ld
import arch.core.mult.Mult
import arch.core.st.St
import chisel3._
import chisel3.util.Valid
import vutils.graph.{ Node, NodeType }

class FuPoolIO(implicit p: Parameters) extends Bundle {
  val cpu            = new FuPoolCpuIO
  val exception      = new FuPoolExceptionIO
  val interrupt      = new FuPoolInterruptIO
  val scheduler      = new FuPoolSchedulerIO
  val rob            = new FuPoolRobIO
  val memory_arbiter = new FuPoolMemoryArbiterIO
  val sb             = new FuPoolSbIO
}

class FuPool(implicit p: Parameters) extends Node(new FuPoolIO) {
  override def nodeType: NodeType  = FuPoolMeta.Type
  override def desiredName: String = "fu_pool"

  private def build(desc: FunctionalUnitDescriptor): Node[_ <: Bundle] =
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
    io.rob.bru(i) := 0.U.asTypeOf(new arch.core.bru.BruResolveIO)

  for (i <- 0 until p(NumSTs))
    io.sb.write(i) := 0.U.asTypeOf(Valid(new arch.core.sb.StoreWriteBundle))

  io.interrupt.view     := 0.U.asTypeOf(new CsrTrapView)
  io.exception.csr_busy := false.B

  private val units = p(FunctionalUnits).zipWithIndex.map { case (desc, idx) =>
    build(desc) -> idx
  }

  private def connectFu(fu: FuIO, idx: Int): Unit = {
    fu.flush := io.exception.flush

    fu.req.valid                 := io.scheduler.reqs(idx).valid
    fu.req.bits                  := io.scheduler.reqs(idx).bits
    io.scheduler.reqs(idx).ready := fu.req.ready

    fu.resp.ready := true.B

    io.scheduler.done(idx).valid := fu.resp.valid && !io.exception.flush
    io.scheduler.done(idx).bits  := fu.resp.bits

    io.rob.done(idx).valid := fu.resp.valid && !io.exception.flush
    io.rob.done(idx).bits  := fu.resp.bits
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

        io.rob.bru(bruIdx).resolved := bru.io.resolve.resolved

        bruIdx += 1

      case csr: Csr =>
        connectFu(csr.io.fu, fuIdx)

        csr.io.ctrl.cycle       := io.cpu.cycle
        csr.io.ctrl.instret     := io.cpu.instret
        csr.io.ctrl.irq         := io.cpu.irq
        csr.io.ctrl.arch_pc     := io.exception.arch_pc
        csr.io.ctrl.trap_update := io.exception.trap_update

        io.interrupt.view     := csr.io.ctrl.view
        io.exception.csr_busy := csr.io.ctrl.busy

        csrIdx += 1

      case _ =>
    }
}
