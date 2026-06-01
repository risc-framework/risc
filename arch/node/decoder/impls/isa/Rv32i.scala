package arch.node.decoder.impls.isa.rv32i

import arch.configs._
import arch.isa._
import arch.node.alu.impls.isa.rv32i.Rv32iAluUopConsts
import arch.node.bru.impls.isa.rv32i.Rv32iBruUopConsts
import arch.node.decoder._
import arch.core.fu.FunctionalUnitType
import arch.node.ld.impls.isa.rv32i.Rv32iMemUopConsts
import arch.core.csr.riscv.RV32ICsrUOpConsts
import arch.core.imm.riscv.RV32IImmConsts
import vutils.graph.{ NodeRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.BitPat

trait Rv32iDecoderUopConsts
    extends RV32IImmConsts
    with Rv32iAluUopConsts
    with Rv32iMemUopConsts
    with RV32ICsrUOpConsts
    with Rv32iBruUopConsts {
  def UOP_X = BitPat("b????????")

  def X = BitPat("b?")
  def Y = BitPat("b1")
  def N = BitPat("b0")

  def FU(t: arch.core.fu.FunctionalUnitType)(implicit p: Parameters): BitPat =
    BitPat(t.index.U(p(FuTypeWidth).W))

  def FU_X(implicit p: Parameters): BitPat   = FU(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_UNKNOWN)
  def FU_ALU(implicit p: Parameters): BitPat = FU(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ALU)
  def FU_LD(implicit p: Parameters): BitPat  = FU(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD)
  def FU_ST(implicit p: Parameters): BitPat  = FU(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST)
  def FU_BRU(implicit p: Parameters): BitPat = FU(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_BRU)
  def FU_CSR(implicit p: Parameters): BitPat = FU(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR)
}

object DecoderRv32iIsa extends RegisteredNodeUtils[DecoderIsaImpl] with Rv32iDecoderUopConsts {
  private val allEncodings =
    RV32I.isa.instrSet.map(s => s.nop.toSeq ++ s.encodings).getOrElse(Seq.empty)

  private def enc(name: String)(implicit p: Parameters): BitPat = {
    val e    = allEncodings
      .find(_.name == name)
      .getOrElse(throw new NoSuchElementException(s"Instruction '$name' not found in RV32I"))
    val bits = (p(ILen) - 1 to 0 by -1).map { i =>
      val valueBit = (e.value >> i) & 1
      val maskBit  = (e.mask >> i) & 1
      if (maskBit == 1) valueBit.toString else "?"
    }.mkString

    BitPat("b" + bits)
  }

  override def utils: DecoderIsaImpl = new DecoderIsaImpl with Rv32iDecoderUopConsts {
    override def value: String = "rv32i"

    override def default(implicit p: Parameters): List[BitPat] =
      List(
        N,     // legal
        N,     // regwrite
        N,     // rs1_valid
        N,     // rs2_valid
        N,     // rd_valid
        N,     // commit_barrier
        IMM_X, // imm_type
        FU_X,  // fu_type
        UOP_X  // uop
      )

    override def table(implicit p: Parameters): Array[(BitPat, List[BitPat])] = Array(
      enc("ADD")    -> List(Y, Y, Y, Y, Y, N, IMM_X, FU_ALU, UOP_ADD),
      enc("SUB")    -> List(Y, Y, Y, Y, Y, N, IMM_X, FU_ALU, UOP_SUB),
      enc("SLL")    -> List(Y, Y, Y, Y, Y, N, IMM_X, FU_ALU, UOP_SLL),
      enc("SLT")    -> List(Y, Y, Y, Y, Y, N, IMM_X, FU_ALU, UOP_SLT),
      enc("SLTU")   -> List(Y, Y, Y, Y, Y, N, IMM_X, FU_ALU, UOP_SLTU),
      enc("XOR")    -> List(Y, Y, Y, Y, Y, N, IMM_X, FU_ALU, UOP_XOR),
      enc("SRL")    -> List(Y, Y, Y, Y, Y, N, IMM_X, FU_ALU, UOP_SRL),
      enc("SRA")    -> List(Y, Y, Y, Y, Y, N, IMM_X, FU_ALU, UOP_SRA),
      enc("OR")     -> List(Y, Y, Y, Y, Y, N, IMM_X, FU_ALU, UOP_OR),
      enc("AND")    -> List(Y, Y, Y, Y, Y, N, IMM_X, FU_ALU, UOP_AND),
      enc("ADDI")   -> List(Y, Y, Y, N, Y, N, IMM_I, FU_ALU, UOP_ADDI),
      enc("SLLI")   -> List(Y, Y, Y, N, Y, N, IMM_I, FU_ALU, UOP_SLLI),
      enc("SLTI")   -> List(Y, Y, Y, N, Y, N, IMM_I, FU_ALU, UOP_SLTI),
      enc("SLTIU")  -> List(Y, Y, Y, N, Y, N, IMM_I, FU_ALU, UOP_SLTIU),
      enc("XORI")   -> List(Y, Y, Y, N, Y, N, IMM_I, FU_ALU, UOP_XORI),
      enc("SRLI")   -> List(Y, Y, Y, N, Y, N, IMM_I, FU_ALU, UOP_SRLI),
      enc("SRAI")   -> List(Y, Y, Y, N, Y, N, IMM_I, FU_ALU, UOP_SRAI),
      enc("ORI")    -> List(Y, Y, Y, N, Y, N, IMM_I, FU_ALU, UOP_ORI),
      enc("ANDI")   -> List(Y, Y, Y, N, Y, N, IMM_I, FU_ALU, UOP_ANDI),
      enc("LB")     -> List(Y, Y, Y, N, Y, N, IMM_I, FU_LD, UOP_LB),
      enc("LH")     -> List(Y, Y, Y, N, Y, N, IMM_I, FU_LD, UOP_LH),
      enc("LW")     -> List(Y, Y, Y, N, Y, N, IMM_I, FU_LD, UOP_LW),
      enc("LBU")    -> List(Y, Y, Y, N, Y, N, IMM_I, FU_LD, UOP_LBU),
      enc("LHU")    -> List(Y, Y, Y, N, Y, N, IMM_I, FU_LD, UOP_LHU),
      enc("SB")     -> List(Y, N, Y, Y, N, N, IMM_S, FU_ST, UOP_SB),
      enc("SH")     -> List(Y, N, Y, Y, N, N, IMM_S, FU_ST, UOP_SH),
      enc("SW")     -> List(Y, N, Y, Y, N, N, IMM_S, FU_ST, UOP_SW),
      enc("BEQ")    -> List(Y, N, Y, Y, N, Y, IMM_B, FU_BRU, UOP_BEQ),
      enc("BNE")    -> List(Y, N, Y, Y, N, Y, IMM_B, FU_BRU, UOP_BNE),
      enc("BLT")    -> List(Y, N, Y, Y, N, Y, IMM_B, FU_BRU, UOP_BLT),
      enc("BGE")    -> List(Y, N, Y, Y, N, Y, IMM_B, FU_BRU, UOP_BGE),
      enc("BLTU")   -> List(Y, N, Y, Y, N, Y, IMM_B, FU_BRU, UOP_BLTU),
      enc("BGEU")   -> List(Y, N, Y, Y, N, Y, IMM_B, FU_BRU, UOP_BGEU),
      enc("LUI")    -> List(Y, Y, N, N, Y, N, IMM_U, FU_ALU, UOP_LUI),
      enc("AUIPC")  -> List(Y, Y, N, N, Y, N, IMM_U, FU_ALU, UOP_AUIPC),
      enc("JAL")    -> List(Y, Y, N, N, Y, N, IMM_J, FU_BRU, UOP_JAL),
      enc("JALR")   -> List(Y, Y, Y, N, Y, N, IMM_I, FU_BRU, UOP_JALR),
      enc("CSRRW")  -> List(Y, Y, Y, N, Y, N, IMM_I, FU_CSR, UOP_CSRRW),
      enc("CSRRS")  -> List(Y, Y, Y, N, Y, N, IMM_I, FU_CSR, UOP_CSRRS),
      enc("CSRRC")  -> List(Y, Y, Y, N, Y, N, IMM_I, FU_CSR, UOP_CSRRC),
      enc("CSRRWI") -> List(Y, Y, N, N, Y, N, IMM_I, FU_CSR, UOP_CSRRWI),
      enc("CSRRSI") -> List(Y, Y, N, N, Y, N, IMM_I, FU_CSR, UOP_CSRRSI),
      enc("CSRRCI") -> List(Y, Y, N, N, Y, N, IMM_I, FU_CSR, UOP_CSRRCI),
      enc("MRET")   -> List(Y, N, N, N, N, N, IMM_X, FU_CSR, UOP_MRET)
    )
  }

  override def registry: NodeRegistry[DecoderIsaImpl] = DecoderIsaFactory
}
