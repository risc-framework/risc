package arch.core.fu.builtin

import arch.core.fu._
import arch.core.imm.ImmUtilsFactory
import arch.core.uop.MicroOp
import arch.core.bru.Bru
import arch.configs._
import chisel3._

class BruFU(implicit p: Parameters) extends OneCycleFunctionalUnit {
  override def desiredName: String = s"${p(ISA).name}_bru_fu"

  override def fuType: FunctionalUnitType = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_BRU

  private val bru       = Module(new Bru)
  private val imm_utils = ImmUtilsFactory.getOrThrow(p(ISA).name)

  private val actualTaken  = Wire(Bool())
  private val actualTarget = Wire(UInt(p(XLen).W))

  override protected def execute(uop: MicroOp): UInt = {
    bru.en   := validReg && !io.flush
    bru.pc   := uop.pc
    bru.src1 := uop.rs1_data
    bru.src2 := uop.rs2_data
    bru.uop  := uop.uop
    bru.imm  := imm_utils.genImm(uop.instr, uop.imm_type)

    val resolvedTaken = bru.jump || bru.taken
    val fallthrough   = uop.pc + p(PCStep).U

    actualTaken  := resolvedTaken
    actualTarget := Mux(resolvedTaken, bru.target, fallthrough)

    fallthrough
  }

  override protected def augmentResp(resp: FunctionalUnitResp, uop: MicroOp): Unit = {
    resp.is_bru        := true.B
    resp.actual_taken  := actualTaken
    resp.actual_target := actualTarget
  }

  driveOneCycle()
}

object BruFUBuilder extends RegisteredFUBuilder {
  override lazy val utils: FUBuilder = new FUBuilder {
    override def name: String                                  = "bru"
    override def fuType: FunctionalUnitType                    = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_BRU
    override def build(implicit p: Parameters): FunctionalUnit = new BruFU
  }
}
