package arch.core.fu.builtin

import arch.core.fu._
import arch.core.uop.MicroOp
import arch.core.csr.{ CoreInterruptIO, CsrFile, CsrUtilsFactory }
import arch.configs._
import chisel3._

class CsrFU(implicit p: Parameters) extends FunctionalUnit {
  override def desiredName: String = s"${p(ISA).name}_csr_fu"

  override def fuType: FunctionalUnitType =
    FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR

  val trap_request = IO(Output(Bool()))
  val trap_target  = IO(Output(UInt(p(XLen).W)))
  val trap_ret_tgt = IO(Output(UInt(p(XLen).W)))
  val trap_ret     = IO(Output(Bool()))
  val is_busy      = IO(Output(Bool()))

  val cycle   = IO(Input(UInt(64.W)))
  val instret = IO(Input(UInt(64.W)))
  val irq     = IO(new CoreInterruptIO)
  val arch_pc = IO(Input(UInt(p(XLen).W)))

  private val csrfile   = Module(new CsrFile)
  private val csr_utils = CsrUtilsFactory.getOrThrow(p(ISA).name)

  private val busy    = RegInit(false.B)
  private val uop_reg = Reg(new MicroOp)

  io.req.ready := !busy || io.resp.ready
  is_busy      := busy

  when(io.flush) {
    busy := false.B
  }.elsewhen(io.req.fire) {
    busy    := true.B
    uop_reg := io.req.bits
  }.elsewhen(io.resp.fire) {
    busy := false.B
  }

  private val active_instr = Mux(busy, uop_reg.instr, 0.U)
  private val active_uop   = Mux(busy, uop_reg.uop, 0.U)
  private val ctrl         = csr_utils.decode(active_uop)

  csrfile.en    := busy
  csrfile.uop   := active_uop
  csrfile.instr := active_instr
  csrfile.addr  := csr_utils.getAddr(active_instr)
  csrfile.src   := uop_reg.rs1_data
  csrfile.pc    := Mux(busy, uop_reg.pc, arch_pc)

  csrfile.extraInputIO("cycle")     := cycle
  csrfile.extraInputIO("instret")   := instret
  csrfile.extraInputIO("timer_irq") := irq.timer_irq
  csrfile.extraInputIO("soft_irq")  := irq.soft_irq
  csrfile.extraInputIO("ext_irq")   := irq.ext_irq

  trap_request := csrfile.trap_request
  trap_target  := csrfile.trap_target
  trap_ret_tgt := csrfile.trap_ret_target
  trap_ret     := busy && ctrl.is_sys

  private val resp = Wire(new FunctionalUnitResp)
  resp := defaultResp(uop_reg, csrfile.rd)

  resp.trap_req     := trap_request
  resp.trap_target  := trap_target
  resp.trap_ret     := trap_ret
  resp.trap_ret_tgt := trap_ret_tgt

  io.resp.valid := busy && !io.flush
  io.resp.bits  := resp
}

object CsrFUBuilder extends RegisteredFUBuilder {
  override lazy val utils: FUBuilder = new FUBuilder {
    override def name: String                                  = "csr"
    override def fuType: FunctionalUnitType                    = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR
    override def build(implicit p: Parameters): FunctionalUnit = new CsrFU
  }
}
