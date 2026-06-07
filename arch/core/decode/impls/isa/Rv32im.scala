package arch.core.decode.impls.isa.rv32im

import arch.configs._
import arch.core.decode._
import arch.core.decode.impls.isa.rv32i.{ DecodeRv32iIsa, Rv32iDecodeConsts }
import arch.core.div.impls.isa.rv32im.Rv32imDivUopConsts
import arch.core.fupool.FunctionalUnitType
import arch.core.mult.impls.isa.rv32im.Rv32imMultUopConsts
import arch.isa._
import vutils.graph.{ NodeRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.BitPat

trait Rv32imDecodeConsts
    extends Rv32iDecodeConsts
    with Rv32imMultUopConsts
    with Rv32imDivUopConsts {
  def FU_MULT(implicit p: Parameters): BitPat = FU(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_MULT)
  def FU_DIV(implicit p: Parameters): BitPat  = FU(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_DIV)
}

object DecodeRv32imIsa extends RegisteredNodeUtils[DecodeIsaImpl] with Rv32imDecodeConsts {
  private def enc(name: String): BitPat =
    RV32IM.isa.bitPat(name)

  override def utils: DecodeIsaImpl = new DecodeIsaImpl {
    private val rv32i = DecodeRv32iIsa.utils

    override def value: String = "rv32im"

    override def default(implicit p: Parameters): List[BitPat] =
      rv32i.default

    override def table(implicit p: Parameters): Array[(BitPat, List[BitPat])] =
      rv32i.table ++ Array(
        enc("MUL")    -> List(Y, Y, Y, Y, Y, N, FU_MULT, UOP_MUL, RF_RS1, RF_RS2, RF_RD, IMM_X),
        enc("MULH")   -> List(Y, Y, Y, Y, Y, N, FU_MULT, UOP_MULH, RF_RS1, RF_RS2, RF_RD, IMM_X),
        enc("MULHSU") -> List(Y, Y, Y, Y, Y, N, FU_MULT, UOP_MULHSU, RF_RS1, RF_RS2, RF_RD, IMM_X),
        enc("MULHU")  -> List(Y, Y, Y, Y, Y, N, FU_MULT, UOP_MULHU, RF_RS1, RF_RS2, RF_RD, IMM_X),
        enc("DIV")    -> List(Y, Y, Y, Y, Y, N, FU_DIV, UOP_DIV, RF_RS1, RF_RS2, RF_RD, IMM_X),
        enc("DIVU")   -> List(Y, Y, Y, Y, Y, N, FU_DIV, UOP_DIVU, RF_RS1, RF_RS2, RF_RD, IMM_X),
        enc("REM")    -> List(Y, Y, Y, Y, Y, N, FU_DIV, UOP_REM, RF_RS1, RF_RS2, RF_RD, IMM_X),
        enc("REMU")   -> List(Y, Y, Y, Y, Y, N, FU_DIV, UOP_REMU, RF_RS1, RF_RS2, RF_RD, IMM_X)
      )

    override def reg(sel: UInt, instr: UInt)(implicit p: Parameters): UInt = rv32i.reg(sel, instr)
    override def readable(addr: UInt)(implicit p: Parameters): Bool        = rv32i.readable(addr)
    override def writable(addr: UInt)(implicit p: Parameters): Bool        = rv32i.writable(addr)
    override def imm(sel: UInt, instr: UInt)(implicit p: Parameters): UInt = rv32i.imm(sel, instr)
  }

  override def registry: NodeRegistry[DecodeIsaImpl] = DecodeIsaFactory
}
