package arch.node.alu.impls.isa.rv32i

import arch.core.imm.ImmUtilsFactory
import arch.core.uop.MicroOp
import arch.node.alu._
import arch.configs._
import vutils.graph.{ NodeRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.{ BitPat, MuxLookup }

trait Rv32iAluUopConsts extends AluConsts {
  private def cat(bps: BitPat*): BitPat = bps.reduce(_ ## _)

  def AM_X  = BitPat("b?")
  def SZ_AM = AM_X.getWidth
  def AM_0  = BitPat("b0")
  def AM_1  = BitPat("b1")

  def AFN_X    = BitPat("b???")
  def SZ_AFN   = AFN_X.getWidth
  def AFN_ADD  = BitPat("b000")
  def AFN_SLL  = BitPat("b001")
  def AFN_SLT  = BitPat("b010")
  def AFN_SLTU = BitPat("b011")
  def AFN_XOR  = BitPat("b100")
  def AFN_SRL  = BitPat("b101")
  def AFN_OR   = BitPat("b110")
  def AFN_AND  = BitPat("b111")

  def UOP_ADD  = cat(A1_RS1, A2_RS2, AM_0, AFN_ADD)
  def UOP_SUB  = cat(A1_RS1, A2_RS2, AM_1, AFN_ADD)
  def UOP_SLL  = cat(A1_RS1, A2_RS2, AM_0, AFN_SLL)
  def UOP_SLT  = cat(A1_RS1, A2_RS2, AM_0, AFN_SLT)
  def UOP_SLTU = cat(A1_RS1, A2_RS2, AM_0, AFN_SLTU)
  def UOP_XOR  = cat(A1_RS1, A2_RS2, AM_0, AFN_XOR)
  def UOP_SRL  = cat(A1_RS1, A2_RS2, AM_0, AFN_SRL)
  def UOP_SRA  = cat(A1_RS1, A2_RS2, AM_1, AFN_SRL)
  def UOP_OR   = cat(A1_RS1, A2_RS2, AM_0, AFN_OR)
  def UOP_AND  = cat(A1_RS1, A2_RS2, AM_0, AFN_AND)

  def UOP_ADDI  = cat(A1_RS1, A2_IMM, AM_0, AFN_ADD)
  def UOP_SLLI  = cat(A1_RS1, A2_IMM, AM_0, AFN_SLL)
  def UOP_SLTI  = cat(A1_RS1, A2_IMM, AM_0, AFN_SLT)
  def UOP_SLTIU = cat(A1_RS1, A2_IMM, AM_0, AFN_SLTU)
  def UOP_XORI  = cat(A1_RS1, A2_IMM, AM_0, AFN_XOR)
  def UOP_SRLI  = cat(A1_RS1, A2_IMM, AM_0, AFN_SRL)
  def UOP_SRAI  = cat(A1_RS1, A2_IMM, AM_1, AFN_SRL)
  def UOP_ORI   = cat(A1_RS1, A2_IMM, AM_0, AFN_OR)
  def UOP_ANDI  = cat(A1_RS1, A2_IMM, AM_0, AFN_AND)

  def UOP_LUI   = cat(A1_ZERO, A2_IMM, AM_0, AFN_ADD)
  def UOP_AUIPC = cat(A1_PC, A2_IMM, AM_0, AFN_ADD)
}

object AluRv32iIsa extends RegisteredNodeUtils[AluIsaImpl] with Rv32iAluUopConsts {
  override def utils: AluIsaImpl = new AluIsaImpl with Rv32iAluUopConsts {
    override def value: String    = "rv32i"
    override def fnTypeWidth: Int = SZ_AFN

    override def decode(uop: UInt): AluCtrl = {
      val ctrl = Wire(new AluCtrl(fnTypeWidth))
      ctrl.sel1 := uop(7, 6)
      ctrl.sel2 := uop(5, 4)
      ctrl.mode := uop(3)
      ctrl.fn   := uop(2, 0)
      ctrl
    }

    override def execute(uop: MicroOp)(implicit p: Parameters): UInt = {
      val immUtils = ImmUtilsFactory.getOrThrow(p(ISA).name)
      val ctrl     = decode(uop.uop)

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
          A2_IMM.value.U(SZ_A2.W)    -> immUtils.genImm(uop.instr, uop.imm_type),
          A2_PCSTEP.value.U(SZ_A2.W) -> p(PCStep).U(p(XLen).W)
        )
      )

      val lt       = src1.asSInt < src2.asSInt
      val ltu      = src1 < src2
      val src2Inv  = Mux(ctrl.mode, ~src2, src2)
      val adderOut = (src1 + src2Inv + ctrl.mode.asUInt)(p(XLen) - 1, 0)
      val shamt    = src2(4, 0)
      val sllOut   = (src1 << shamt)(p(XLen) - 1, 0)
      val srlOut   = Mux(ctrl.mode, (src1.asSInt >> shamt).asUInt, src1 >> shamt)(p(XLen) - 1, 0)

      MuxLookup(ctrl.fn, 0.U(p(XLen).W))(
        Seq(
          AFN_ADD.value.U(SZ_AFN.W)  -> adderOut,
          AFN_SLL.value.U(SZ_AFN.W)  -> sllOut,
          AFN_SLT.value.U(SZ_AFN.W)  -> lt.asUInt,
          AFN_SLTU.value.U(SZ_AFN.W) -> ltu.asUInt,
          AFN_XOR.value.U(SZ_AFN.W)  -> (src1 ^ src2),
          AFN_SRL.value.U(SZ_AFN.W)  -> srlOut,
          AFN_OR.value.U(SZ_AFN.W)   -> (src1 | src2),
          AFN_AND.value.U(SZ_AFN.W)  -> (src1 & src2)
        )
      )
    }
  }

  override def registry: NodeRegistry[AluIsaImpl] = AluIsaFactory
}
