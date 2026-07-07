package arch.core.alu.impls.isa.rv32i

import arch.core.fupool.FuReq
import arch.core.alu._
import arch.configs._
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.{ BitPat, MuxLookup }

trait Rv32iAluUopConsts {
  private def cat(bps: BitPat*): BitPat = bps.reduce(_ ## _)

  def A1_X    = BitPat("b??")
  def SZ_A1   = A1_X.getWidth
  def A1_ZERO = BitPat("b00")
  def A1_RS1  = BitPat("b01")
  def A1_PC   = BitPat("b10")

  def A1(sel: BitPat): UInt = sel.value.U(SZ_A1.W)

  def A2_X      = BitPat("b??")
  def SZ_A2     = A2_X.getWidth
  def A2_ZERO   = BitPat("b00")
  def A2_RS2    = BitPat("b01")
  def A2_IMM    = BitPat("b10")
  def A2_PCSTEP = BitPat("b11")

  def A2(sel: BitPat): UInt = sel.value.U(SZ_A2.W)

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

  def AFN(fn: BitPat): UInt = fn.value.asUInt(SZ_AFN.W)

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

  def decode(uop: UInt): (UInt, UInt, Bool, UInt) =
    (uop(7, 6), uop(5, 4), uop(3), uop(2, 0))
}

object AluRv32iIsa extends RegisteredNodeUtils[AluIsaImpl] with Rv32iAluUopConsts {
  override def utils: AluIsaImpl = new AluIsaImpl {
    override def value: String = "rv32i"

    override def execute(uop: FuReq)(implicit p: Parameters): UInt = {
      val ctrl = decode(uop.uop)

      val src1 = MuxLookup(ctrl._1, 0.U(p(XLen).W))(
        Seq(
          A1(A1_ZERO) -> 0.U(p(XLen).W),
          A1(A1_RS1)  -> uop.rs1_data,
          A1(A1_PC)   -> uop.pc
        )
      )

      val src2 = MuxLookup(ctrl._2, 0.U(p(XLen).W))(
        Seq(
          A2(A2_ZERO)   -> 0.U(p(XLen).W),
          A2(A2_RS2)    -> uop.rs2_data,
          A2(A2_IMM)    -> uop.imm,
          A2(A2_PCSTEP) -> p(PCStep).U(p(XLen).W)
        )
      )

      val lt       = src1.asSInt < src2.asSInt
      val ltu      = src1 < src2
      val src2Inv  = Mux(ctrl._3, ~src2, src2)
      val adderOut = (src1 + src2Inv + ctrl._3.asUInt)(p(XLen) - 1, 0)
      val shamt    = src2(4, 0)
      val sllOut   = (src1 << shamt)(p(XLen) - 1, 0)
      val srlOut   = Mux(ctrl._3, (src1.asSInt >> shamt).asUInt, src1 >> shamt)(p(XLen) - 1, 0)

      MuxLookup(ctrl._4, 0.U(p(XLen).W))(
        Seq(
          AFN(AFN_ADD)  -> adderOut,
          AFN(AFN_SLL)  -> sllOut,
          AFN(AFN_SLT)  -> lt.asUInt,
          AFN(AFN_SLTU) -> ltu.asUInt,
          AFN(AFN_XOR)  -> (src1 ^ src2),
          AFN(AFN_SRL)  -> srlOut,
          AFN(AFN_OR)   -> (src1 | src2),
          AFN(AFN_AND)  -> (src1 & src2)
        )
      )
    }
  }

  override def registry: NodeDimensionRegistry[AluIsaImpl] =
    AluIsaFactory
}
