package arch.core.decode.impls.isa.rv32i

import arch.configs._
import arch.core.alu.impls.isa.rv32i.Rv32iAluUopConsts
import arch.core.bru.impls.isa.rv32i.Rv32iBruUopConsts
import arch.core.csr.impls.file.rv32i.Rv32iCsrUopConsts
import arch.core.decode._
import arch.core.fupool.FunctionalUnitType
import arch.core.ld.impls.isa.rv32i.Rv32iMemUopConsts
import arch.isa._
import chisel3._
import chisel3.util.{ BitPat, log2Ceil, MuxLookup, Cat, Fill }
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }

trait Rv32iDecodeConsts
    extends Rv32iAluUopConsts
    with Rv32iMemUopConsts
    with Rv32iCsrUopConsts
    with Rv32iBruUopConsts {
  def X = BitPat("b?")
  def Y = BitPat("b1")
  def N = BitPat("b0")

  def RF_X    = BitPat("b??")
  def SZ_RF   = RF_X.getWidth
  def RF_ZERO = BitPat("b00")
  def RF_RS1  = BitPat("b01")
  def RF_RS2  = BitPat("b10")
  def RF_RD   = BitPat("b11")

  def RF(sel: BitPat): UInt = sel.value.U(SZ_RF.W)

  def IMM_X   = BitPat("b???")
  def SZ_IMM  = IMM_X.getWidth
  def IMM_I   = BitPat("b000")
  def IMM_S   = BitPat("b001")
  def IMM_B   = BitPat("b010")
  def IMM_U   = BitPat("b011")
  def IMM_J   = BitPat("b100")
  def IMM_CSR = BitPat("b101")

  def IMM(sel: BitPat): UInt = sel.value.U(SZ_IMM.W)

  def UOP_X  = BitPat("b????????")
  def SZ_UOP = UOP_X.getWidth

  def FU(t: FunctionalUnitType)(implicit p: Parameters): BitPat = BitPat(
    t.index.U(p(FuTypeWidth).W)
  )
  def FU_X(implicit p: Parameters): BitPat                      = FU(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_UNKNOWN)
  def FU_ALU(implicit p: Parameters): BitPat                    = FU(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ALU)
  def FU_LD(implicit p: Parameters): BitPat                     = FU(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD)
  def FU_ST(implicit p: Parameters): BitPat                     = FU(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST)
  def FU_BRU(implicit p: Parameters): BitPat                    = FU(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_BRU)
  def FU_CSR(implicit p: Parameters): BitPat                    = FU(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR)
}

object DecodeRv32iIsa extends RegisteredNodeUtils[DecodeIsaImpl] with Rv32iDecodeConsts {
  private def enc(name: String): BitPat =
    Rv32i.isa.bitPat(name)

  override def utils: DecodeIsaImpl = new DecodeIsaImpl {
    override def value: String = "rv32i"

    override def uopWidth: Int = SZ_UOP

    override def default(implicit p: Parameters): List[BitPat] =
      List(
        N,       // legal
        N,       // regwrite
        N,       // rs1_read
        N,       // rs2_read
        N,       // rd_write
        N,       // commit_barrier
        FU_X,    // fu_type
        UOP_X,   // uop
        RF_ZERO, // rs1 selector
        RF_ZERO, // rs2 selector
        RF_ZERO, // rd selector
        IMM_X    // imm selector
      )

    override def table(implicit p: Parameters): Array[(BitPat, List[BitPat])] = Array(
      enc("ADD")    -> List(Y, Y, Y, Y, Y, N, FU_ALU, UOP_ADD, RF_RS1, RF_RS2, RF_RD, IMM_X),
      enc("SUB")    -> List(Y, Y, Y, Y, Y, N, FU_ALU, UOP_SUB, RF_RS1, RF_RS2, RF_RD, IMM_X),
      enc("SLL")    -> List(Y, Y, Y, Y, Y, N, FU_ALU, UOP_SLL, RF_RS1, RF_RS2, RF_RD, IMM_X),
      enc("SLT")    -> List(Y, Y, Y, Y, Y, N, FU_ALU, UOP_SLT, RF_RS1, RF_RS2, RF_RD, IMM_X),
      enc("SLTU")   -> List(Y, Y, Y, Y, Y, N, FU_ALU, UOP_SLTU, RF_RS1, RF_RS2, RF_RD, IMM_X),
      enc("XOR")    -> List(Y, Y, Y, Y, Y, N, FU_ALU, UOP_XOR, RF_RS1, RF_RS2, RF_RD, IMM_X),
      enc("SRL")    -> List(Y, Y, Y, Y, Y, N, FU_ALU, UOP_SRL, RF_RS1, RF_RS2, RF_RD, IMM_X),
      enc("SRA")    -> List(Y, Y, Y, Y, Y, N, FU_ALU, UOP_SRA, RF_RS1, RF_RS2, RF_RD, IMM_X),
      enc("OR")     -> List(Y, Y, Y, Y, Y, N, FU_ALU, UOP_OR, RF_RS1, RF_RS2, RF_RD, IMM_X),
      enc("AND")    -> List(Y, Y, Y, Y, Y, N, FU_ALU, UOP_AND, RF_RS1, RF_RS2, RF_RD, IMM_X),
      enc("ADDI")   -> List(Y, Y, Y, N, Y, N, FU_ALU, UOP_ADDI, RF_RS1, RF_ZERO, RF_RD, IMM_I),
      enc("SLLI")   -> List(Y, Y, Y, N, Y, N, FU_ALU, UOP_SLLI, RF_RS1, RF_ZERO, RF_RD, IMM_I),
      enc("SLTI")   -> List(Y, Y, Y, N, Y, N, FU_ALU, UOP_SLTI, RF_RS1, RF_ZERO, RF_RD, IMM_I),
      enc("SLTIU")  -> List(Y, Y, Y, N, Y, N, FU_ALU, UOP_SLTIU, RF_RS1, RF_ZERO, RF_RD, IMM_I),
      enc("XORI")   -> List(Y, Y, Y, N, Y, N, FU_ALU, UOP_XORI, RF_RS1, RF_ZERO, RF_RD, IMM_I),
      enc("SRLI")   -> List(Y, Y, Y, N, Y, N, FU_ALU, UOP_SRLI, RF_RS1, RF_ZERO, RF_RD, IMM_I),
      enc("SRAI")   -> List(Y, Y, Y, N, Y, N, FU_ALU, UOP_SRAI, RF_RS1, RF_ZERO, RF_RD, IMM_I),
      enc("ORI")    -> List(Y, Y, Y, N, Y, N, FU_ALU, UOP_ORI, RF_RS1, RF_ZERO, RF_RD, IMM_I),
      enc("ANDI")   -> List(Y, Y, Y, N, Y, N, FU_ALU, UOP_ANDI, RF_RS1, RF_ZERO, RF_RD, IMM_I),
      enc("LB")     -> List(Y, Y, Y, N, Y, N, FU_LD, UOP_LB, RF_RS1, RF_ZERO, RF_RD, IMM_I),
      enc("LH")     -> List(Y, Y, Y, N, Y, N, FU_LD, UOP_LH, RF_RS1, RF_ZERO, RF_RD, IMM_I),
      enc("LW")     -> List(Y, Y, Y, N, Y, N, FU_LD, UOP_LW, RF_RS1, RF_ZERO, RF_RD, IMM_I),
      enc("LBU")    -> List(Y, Y, Y, N, Y, N, FU_LD, UOP_LBU, RF_RS1, RF_ZERO, RF_RD, IMM_I),
      enc("LHU")    -> List(Y, Y, Y, N, Y, N, FU_LD, UOP_LHU, RF_RS1, RF_ZERO, RF_RD, IMM_I),
      enc("JALR")   -> List(Y, Y, Y, N, Y, N, FU_BRU, UOP_JALR, RF_RS1, RF_ZERO, RF_RD, IMM_I),
      enc("CSRRW")  -> List(Y, Y, Y, N, Y, N, FU_CSR, UOP_CSRRW, RF_RS1, RF_ZERO, RF_RD, IMM_CSR),
      enc("CSRRS")  -> List(Y, Y, Y, N, Y, N, FU_CSR, UOP_CSRRS, RF_RS1, RF_ZERO, RF_RD, IMM_CSR),
      enc("CSRRC")  -> List(Y, Y, Y, N, Y, N, FU_CSR, UOP_CSRRC, RF_RS1, RF_ZERO, RF_RD, IMM_CSR),
      enc("CSRRWI") -> List(Y, Y, N, N, Y, N, FU_CSR, UOP_CSRRWI, RF_ZERO, RF_ZERO, RF_RD, IMM_CSR),
      enc("CSRRSI") -> List(Y, Y, N, N, Y, N, FU_CSR, UOP_CSRRSI, RF_ZERO, RF_ZERO, RF_RD, IMM_CSR),
      enc("CSRRCI") -> List(Y, Y, N, N, Y, N, FU_CSR, UOP_CSRRCI, RF_ZERO, RF_ZERO, RF_RD, IMM_CSR),
      enc("SB")     -> List(Y, N, Y, Y, N, N, FU_ST, UOP_SB, RF_RS1, RF_RS2, RF_ZERO, IMM_S),
      enc("SH")     -> List(Y, N, Y, Y, N, N, FU_ST, UOP_SH, RF_RS1, RF_RS2, RF_ZERO, IMM_S),
      enc("SW")     -> List(Y, N, Y, Y, N, N, FU_ST, UOP_SW, RF_RS1, RF_RS2, RF_ZERO, IMM_S),
      enc("BEQ")    -> List(Y, N, Y, Y, N, Y, FU_BRU, UOP_BEQ, RF_RS1, RF_RS2, RF_ZERO, IMM_B),
      enc("BNE")    -> List(Y, N, Y, Y, N, Y, FU_BRU, UOP_BNE, RF_RS1, RF_RS2, RF_ZERO, IMM_B),
      enc("BLT")    -> List(Y, N, Y, Y, N, Y, FU_BRU, UOP_BLT, RF_RS1, RF_RS2, RF_ZERO, IMM_B),
      enc("BGE")    -> List(Y, N, Y, Y, N, Y, FU_BRU, UOP_BGE, RF_RS1, RF_RS2, RF_ZERO, IMM_B),
      enc("BLTU")   -> List(Y, N, Y, Y, N, Y, FU_BRU, UOP_BLTU, RF_RS1, RF_RS2, RF_ZERO, IMM_B),
      enc("BGEU")   -> List(Y, N, Y, Y, N, Y, FU_BRU, UOP_BGEU, RF_RS1, RF_RS2, RF_ZERO, IMM_B),
      enc("LUI")    -> List(Y, Y, N, N, Y, N, FU_ALU, UOP_LUI, RF_ZERO, RF_ZERO, RF_RD, IMM_U),
      enc("AUIPC")  -> List(Y, Y, N, N, Y, N, FU_ALU, UOP_AUIPC, RF_ZERO, RF_ZERO, RF_RD, IMM_U),
      enc("JAL")    -> List(Y, Y, N, N, Y, N, FU_BRU, UOP_JAL, RF_ZERO, RF_ZERO, RF_RD, IMM_J),
      enc("ECALL")  -> List(Y, N, N, N, N, Y, FU_CSR, UOP_MRET, RF_ZERO, RF_ZERO, RF_ZERO, IMM_X),
      enc("EBREAK") -> List(Y, N, N, N, N, Y, FU_CSR, UOP_MRET, RF_ZERO, RF_ZERO, RF_ZERO, IMM_X),
      enc("MRET")   -> List(Y, N, N, N, N, Y, FU_CSR, UOP_MRET, RF_ZERO, RF_ZERO, RF_ZERO, IMM_X)
    )

    override def reg(sel: UInt, instr: UInt)(implicit p: Parameters): UInt = {
      val regW = log2Ceil(p(NumArchRegs))

      MuxLookup(sel, 0.U(regW.W))(
        Seq(
          RF(RF_ZERO) -> 0.U(regW.W),
          RF(RF_RS1)  -> instr(19, 15),
          RF(RF_RS2)  -> instr(24, 20),
          RF(RF_RD)   -> instr(11, 7)
        )
      )
    }

    override def readable(addr: UInt)(implicit p: Parameters): Bool =
      addr =/= 0.U

    override def writable(addr: UInt)(implicit p: Parameters): Bool =
      addr =/= 0.U

    override def imm(sel: UInt, instr: UInt)(implicit p: Parameters): UInt =
      MuxLookup(sel, 0.U(p(XLen).W))(
        Seq(
          IMM(IMM_I)   -> Cat(Fill(p(XLen) - 12, instr(31)), instr(31, 20)),
          IMM(IMM_S)   -> Cat(Fill(p(XLen) - 12, instr(31)), instr(31, 25), instr(11, 7)),
          IMM(IMM_B)   -> Cat(
            Fill(p(XLen) - 13, instr(31)),
            instr(31),
            instr(7),
            instr(30, 25),
            instr(11, 8),
            0.U(1.W)
          ),
          IMM(IMM_U)   -> Cat(instr(31, 12), Fill(12, 0.U)),
          IMM(IMM_J)   -> Cat(
            Fill(p(XLen) - 21, instr(31)),
            instr(31),
            instr(19, 12),
            instr(20),
            instr(30, 21),
            0.U(1.W)
          ),
          IMM(IMM_CSR) -> Cat(Fill(p(XLen) - 5, 0.U), instr(19, 15))
        )
      )
  }

  override def registry: NodeDimensionRegistry[DecodeIsaImpl] =
    DecodeIsaFactory
}
