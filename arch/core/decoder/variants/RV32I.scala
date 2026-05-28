package arch.core.decoder.riscv

import arch.core.decoder._
import arch.core.imm.riscv.RV32IImmConsts
import arch.core.alu.riscv.RV32IAluUopConsts
import arch.core.lsu.riscv.RV32IMemUOpConsts
import arch.core.bru.riscv.RV32IBruUOpConsts
import arch.core.csr.riscv.RV32ICsrUOpConsts
import arch.configs._
import arch.isa._
import chisel3._
import chisel3.util.BitPat

trait RV32IUOp
    extends RV32IImmConsts
    with RV32IAluUopConsts
    with RV32IMemUOpConsts
    with RV32ICsrUOpConsts
    with RV32IBruUOpConsts {
  def UOP_X = BitPat("b????????")

  def X = BitPat("b?")
  def Y = BitPat("b1")
  def N = BitPat("b0")
}

object RV32IDecoderUtils extends RegisteredUtils[DecoderUtils] with RV32IUOp {
  private val allEncodings =
    RV32I.isa.instrSet
      .map(s => s.nop.toSeq ++ s.encodings)
      .getOrElse(Seq.empty)

  private def enc(name: String): BitPat = {
    val e = allEncodings
      .find(_.name == name)
      .getOrElse(throw new NoSuchElementException(s"Instruction '$name' not found in RV32I"))

    val bits = (p(ILen) - 1 to 0 by -1).map { i =>
      val valueBit = (e.value >> i) & 1
      val maskBit  = (e.mask >> i) & 1
      if (maskBit == 1) valueBit.toString else "?"
    }.mkString

    BitPat("b" + bits)
  }

  override def utils: DecoderUtils = new DecoderUtils {
    override def name: String = "rv32i"

    override def default: List[BitPat] =
      List(
        N,     // legal
        N,     // regwrite
        N,     // rs1_valid
        N,     // rs2_valid
        N,     // rd_valid
        N,     // commit_barrier
        IMM_X, // imm_type
        N,     // alu
        N,     // mult
        N,     // div
        N,     // load
        N,     // store
        N,     // bru
        N,     // csr
        UOP_X  // uop
      )

    override def decode(instr: UInt): DecodedOutput = {
      val sigs    = Wire(new DecodedOutput)
      val decoder = DecodeLogic(instr, default, table)

      sigs.legal          := decoder(0).asBool
      sigs.regwrite       := decoder(1).asBool
      sigs.rs1_valid      := decoder(2).asBool
      sigs.rs2_valid      := decoder(3).asBool
      sigs.rd_valid       := decoder(4).asBool
      sigs.commit_barrier := decoder(5).asBool
      sigs.imm_type       := decoder(6)

      sigs.alu   := decoder(7).asBool
      sigs.mult  := decoder(8).asBool
      sigs.div   := decoder(9).asBool
      sigs.load  := decoder(10).asBool
      sigs.store := decoder(11).asBool
      sigs.bru   := decoder(12).asBool
      sigs.csr   := decoder(13).asBool

      sigs.uop := decoder(14)

      sigs
    }

    override def table: Array[(BitPat, List[BitPat])] = Array(
      enc("ADD")    -> List(Y, Y, Y, Y, Y, N, IMM_X, Y, N, N, N, N, N, N, UOP_ADD),
      enc("SUB")    -> List(Y, Y, Y, Y, Y, N, IMM_X, Y, N, N, N, N, N, N, UOP_SUB),
      enc("SLL")    -> List(Y, Y, Y, Y, Y, N, IMM_X, Y, N, N, N, N, N, N, UOP_SLL),
      enc("SLT")    -> List(Y, Y, Y, Y, Y, N, IMM_X, Y, N, N, N, N, N, N, UOP_SLT),
      enc("SLTU")   -> List(Y, Y, Y, Y, Y, N, IMM_X, Y, N, N, N, N, N, N, UOP_SLTU),
      enc("XOR")    -> List(Y, Y, Y, Y, Y, N, IMM_X, Y, N, N, N, N, N, N, UOP_XOR),
      enc("SRL")    -> List(Y, Y, Y, Y, Y, N, IMM_X, Y, N, N, N, N, N, N, UOP_SRL),
      enc("SRA")    -> List(Y, Y, Y, Y, Y, N, IMM_X, Y, N, N, N, N, N, N, UOP_SRA),
      enc("OR")     -> List(Y, Y, Y, Y, Y, N, IMM_X, Y, N, N, N, N, N, N, UOP_OR),
      enc("AND")    -> List(Y, Y, Y, Y, Y, N, IMM_X, Y, N, N, N, N, N, N, UOP_AND),
      enc("ADDI")   -> List(Y, Y, Y, N, Y, N, IMM_I, Y, N, N, N, N, N, N, UOP_ADDI),
      enc("SLLI")   -> List(Y, Y, Y, N, Y, N, IMM_I, Y, N, N, N, N, N, N, UOP_SLLI),
      enc("SLTI")   -> List(Y, Y, Y, N, Y, N, IMM_I, Y, N, N, N, N, N, N, UOP_SLTI),
      enc("SLTIU")  -> List(Y, Y, Y, N, Y, N, IMM_I, Y, N, N, N, N, N, N, UOP_SLTIU),
      enc("XORI")   -> List(Y, Y, Y, N, Y, N, IMM_I, Y, N, N, N, N, N, N, UOP_XORI),
      enc("SRLI")   -> List(Y, Y, Y, N, Y, N, IMM_I, Y, N, N, N, N, N, N, UOP_SRLI),
      enc("SRAI")   -> List(Y, Y, Y, N, Y, N, IMM_I, Y, N, N, N, N, N, N, UOP_SRAI),
      enc("ORI")    -> List(Y, Y, Y, N, Y, N, IMM_I, Y, N, N, N, N, N, N, UOP_ORI),
      enc("ANDI")   -> List(Y, Y, Y, N, Y, N, IMM_I, Y, N, N, N, N, N, N, UOP_ANDI),
      enc("LB")     -> List(Y, Y, Y, N, Y, N, IMM_I, N, N, N, Y, N, N, N, UOP_LB),
      enc("LH")     -> List(Y, Y, Y, N, Y, N, IMM_I, N, N, N, Y, N, N, N, UOP_LH),
      enc("LW")     -> List(Y, Y, Y, N, Y, N, IMM_I, N, N, N, Y, N, N, N, UOP_LW),
      enc("LBU")    -> List(Y, Y, Y, N, Y, N, IMM_I, N, N, N, Y, N, N, N, UOP_LBU),
      enc("LHU")    -> List(Y, Y, Y, N, Y, N, IMM_I, N, N, N, Y, N, N, N, UOP_LHU),
      enc("SB")     -> List(Y, N, Y, Y, N, N, IMM_S, N, N, N, N, Y, N, N, UOP_SB),
      enc("SH")     -> List(Y, N, Y, Y, N, N, IMM_S, N, N, N, N, Y, N, N, UOP_SH),
      enc("SW")     -> List(Y, N, Y, Y, N, N, IMM_S, N, N, N, N, Y, N, N, UOP_SW),
      enc("BEQ")    -> List(Y, N, Y, Y, N, Y, IMM_B, N, N, N, N, N, Y, N, UOP_BEQ),
      enc("BNE")    -> List(Y, N, Y, Y, N, Y, IMM_B, N, N, N, N, N, Y, N, UOP_BNE),
      enc("BLT")    -> List(Y, N, Y, Y, N, Y, IMM_B, N, N, N, N, N, Y, N, UOP_BLT),
      enc("BGE")    -> List(Y, N, Y, Y, N, Y, IMM_B, N, N, N, N, N, Y, N, UOP_BGE),
      enc("BLTU")   -> List(Y, N, Y, Y, N, Y, IMM_B, N, N, N, N, N, Y, N, UOP_BLTU),
      enc("BGEU")   -> List(Y, N, Y, Y, N, Y, IMM_B, N, N, N, N, N, Y, N, UOP_BGEU),
      enc("LUI")    -> List(Y, Y, N, N, Y, N, IMM_U, Y, N, N, N, N, N, N, UOP_LUI),
      enc("AUIPC")  -> List(Y, Y, N, N, Y, N, IMM_U, Y, N, N, N, N, N, N, UOP_AUIPC),
      enc("JAL")    -> List(Y, Y, N, N, Y, N, IMM_J, N, N, N, N, N, Y, N, UOP_JAL),
      enc("JALR")   -> List(Y, Y, Y, N, Y, N, IMM_I, N, N, N, N, N, Y, N, UOP_JALR),
      enc("CSRRW")  -> List(Y, Y, Y, N, Y, N, IMM_I, N, N, N, N, N, N, Y, UOP_CSRRW),
      enc("CSRRS")  -> List(Y, Y, Y, N, Y, N, IMM_I, N, N, N, N, N, N, Y, UOP_CSRRS),
      enc("CSRRC")  -> List(Y, Y, Y, N, Y, N, IMM_I, N, N, N, N, N, N, Y, UOP_CSRRC),
      enc("CSRRWI") -> List(Y, Y, N, N, Y, N, IMM_I, N, N, N, N, N, N, Y, UOP_CSRRWI),
      enc("CSRRSI") -> List(Y, Y, N, N, Y, N, IMM_I, N, N, N, N, N, N, Y, UOP_CSRRSI),
      enc("CSRRCI") -> List(Y, Y, N, N, Y, N, IMM_I, N, N, N, N, N, N, Y, UOP_CSRRCI),
      enc("MRET")   -> List(Y, N, N, N, N, N, IMM_X, N, N, N, N, N, N, Y, UOP_MRET)
    )
  }

  override def factory: UtilsFactory[DecoderUtils] = DecoderUtilsFactory
}
