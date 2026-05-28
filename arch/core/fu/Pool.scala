package arch.core.fu

import arch.core.lsu.{ StoreForwardPort, StoreWriteBundle }
import arch.core.csr.CoreInterruptIO
import arch.core.uop.MicroOp
import arch.core.fu.builtin.{ CsrFU, LoadFU, StoreFU }
import arch.configs._
import vcache.CachePortIO
import chisel3._
import chisel3.util.{ Decoupled, Valid }
import scala.reflect.ClassTag

class FunctionalUnitPoolIO(implicit p: Parameters) extends Bundle {
  private val numLoadFUs  =
    p(FunctionalUnits).count(_.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD)
  private val numStoreFUs =
    p(FunctionalUnits).count(_.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST)
  private val numCsrFUs   =
    p(FunctionalUnits).count(_.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR)

  val req   = Vec(p(NumFUs), Flipped(Decoupled(new MicroOp)))
  val done  = Output(Vec(p(NumFUs), Valid(new FunctionalUnitResp)))
  val flush = Input(Bool())

  val ld_mem             = Vec(numLoadFUs, new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams)))
  val ld_mmio            = Vec(numLoadFUs, new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams)))
  val ld_sb_fwd          = Vec(numLoadFUs, Flipped(new StoreForwardPort))
  val ld_sb_oldest_valid = Input(Vec(numLoadFUs, Bool()))
  val ld_sb_oldest_seq   = Input(Vec(numLoadFUs, UInt(64.W)))
  val ld_busy            = Output(Vec(numLoadFUs, Bool()))

  val st_sb_write = Output(Vec(numStoreFUs, Valid(new StoreWriteBundle)))
  val st_busy     = Output(Vec(numStoreFUs, Bool()))

  val csr_trap_request = Output(Vec(numCsrFUs, Bool()))
  val csr_trap_target  = Output(Vec(numCsrFUs, UInt(p(XLen).W)))
  val csr_trap_ret_tgt = Output(Vec(numCsrFUs, UInt(p(XLen).W)))
  val csr_trap_ret     = Output(Vec(numCsrFUs, Bool()))
  val csr_is_busy      = Output(Vec(numCsrFUs, Bool()))
  val csr_cycle        = Input(UInt(64.W))
  val csr_instret      = Input(UInt(64.W))
  val csr_irq          = Vec(numCsrFUs, new CoreInterruptIO)
  val csr_arch_pc      = Input(Vec(numCsrFUs, UInt(p(XLen).W)))
}

class FunctionalUnitPool(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_fu_pool"

  val io = IO(new FunctionalUnitPoolIO)

  val units: Seq[FunctionalUnit] = FUFactory.instantiateAll()

  require(
    units.size == p(NumFUs),
    s"FunctionalUnitPool: instantiated ${units.size} FUs, expected ${p(NumFUs)}"
  )

  for ((fu, i) <- units.zipWithIndex) {
    fu.io.flush      := io.flush
    fu.io.req <> io.req(i)
    fu.io.resp.ready := true.B
    io.done(i).valid := fu.io.resp.valid
    io.done(i).bits  := fu.io.resp.bits
  }

  private var ldIdx  = 0
  private var stIdx  = 0
  private var csrIdx = 0

  for (fu <- units)
    fu match {
      case ld: LoadFU =>
        ld.mem <> io.ld_mem(ldIdx)
        ld.mmio <> io.ld_mmio(ldIdx)
        ld.sbFwd <> io.ld_sb_fwd(ldIdx)
        ld.sbOldestValid  := io.ld_sb_oldest_valid(ldIdx)
        ld.sbOldestSeq    := io.ld_sb_oldest_seq(ldIdx)
        io.ld_busy(ldIdx) := ld.busy
        ldIdx += 1

      case st: StoreFU =>
        io.st_sb_write(stIdx) := st.sbWrite
        io.st_busy(stIdx)     := st.busy
        stIdx += 1

      case csr: CsrFU =>
        csr.cycle                   := io.csr_cycle
        csr.instret                 := io.csr_instret
        csr.arch_pc                 := io.csr_arch_pc(csrIdx)
        csr.irq.timer_irq           := io.csr_irq(csrIdx).timer_irq
        csr.irq.soft_irq            := io.csr_irq(csrIdx).soft_irq
        csr.irq.ext_irq             := io.csr_irq(csrIdx).ext_irq
        io.csr_trap_request(csrIdx) := csr.trap_request
        io.csr_trap_target(csrIdx)  := csr.trap_target
        io.csr_trap_ret_tgt(csrIdx) := csr.trap_ret_tgt
        io.csr_trap_ret(csrIdx)     := csr.trap_ret
        io.csr_is_busy(csrIdx)      := csr.is_busy
        csrIdx += 1

      case _ =>
    }

  def collectUnits[T <: FunctionalUnit](implicit ct: ClassTag[T]): Seq[T] = units.collect {
    case fu: T => fu
  }

  def count(tpe: FunctionalUnitType): Int = units.count(_.fuType == tpe)
}
