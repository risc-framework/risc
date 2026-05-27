package arch.core.fu.builtin

import arch.core.fu._
import arch.core.imm._
import arch.core.uop._
import arch.core.alu._
import arch.configs._
import chisel3._
import chisel3.util.MuxLookup

class AluFU(implicit p: Parameters) extends OneCycleFunctionalUnit with AluConsts {
  override def desiredName: String = s"${p(ISA).name}_alu_fu"

  override def fuType: FunctionalUnitType = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ALU

  private val alu       = Module(new Alu)
  private val alu_utils = AluUtilsFactory.getOrThrow(p(ISA).name)
  private val imm_utils = ImmUtilsFactory.getOrThrow(p(ISA).name)

  override protected def execute(uop: MicroOp): UInt = {
    val ctrl = alu_utils.decode(uop.uop)

    val src1 = MuxLookup(ctrl.sel1, 0.U(p(XLen).W))(
      Seq(
        A1_ZERO.value.U(SZ_A1.W) -> 0.U(p(XLen).W),
        A1_RS1.value.U(SZ_A1.W)  -> uop.rs1_data,
        A1_PC.value.U(SZ_A1.W)   -> uop.pc
      )
    )

    val src2 = MuxLookup(ctrl.sel2, 0.U(p(XLen).W))(
      Seq(
        A2_ZERO.value.U(SZ_A2.W)   -> 0.U(p(XLen).W),
        A2_RS2.value.U(SZ_A2.W)    -> uop.rs2_data,
        A2_IMM.value.U(SZ_A2.W)    -> imm_utils.genImm(uop.instr, uop.imm_type),
        A2_PCSTEP.value.U(SZ_A2.W) -> p(PCStep).U(p(XLen).W)
      )
    )

    alu.en   := validReg
    alu.src1 := src1
    alu.src2 := src2
    alu.fn   := ctrl.fn
    alu.mode := ctrl.mode

    alu.result
  }

  driveOneCycle()
}

object AluFUBuilder extends RegisteredFunctionalUnitBuilder {
  override lazy val utils: FunctionalUnitBuilder = new FunctionalUnitBuilder {
    override def name: String                                  = "alu"
    override def fuType: FunctionalUnitType                    = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ALU
    override def build(implicit p: Parameters): FunctionalUnit = new AluFU
  }
}
