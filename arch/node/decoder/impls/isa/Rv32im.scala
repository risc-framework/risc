package arch.node.decoder.impls.isa.rv32im

import arch.node.decoder._
import arch.node.decoder.impls.isa.rv32i.{ Rv32iDecoderUopConsts, DecoderRv32iIsa }
import arch.node.div.impls.isa.rv32im.Rv32imDivUopConsts
import arch.node.mult.impls.isa.rv32im.Rv32imMultUopConsts
import arch.core.fu.FunctionalUnitType
import arch.isa._
import arch.configs._
import vutils.graph.{ NodeRegistry, RegisteredNodeUtils }
import chisel3.util.BitPat

trait Rv32imDecoderUopConsts
    extends Rv32iDecoderUopConsts
    with Rv32imMultUopConsts
    with Rv32imDivUopConsts {
  def FU_MULT(implicit p: Parameters): BitPat = FU(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_MULT)
  def FU_DIV(implicit p: Parameters): BitPat  = FU(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_DIV)
}

object DecoderRv32imIsa extends RegisteredNodeUtils[DecoderIsaImpl] with Rv32imDecoderUopConsts {
  private val allEncodings =
    RV32IM.isa.instrSet.map(s => s.nop.toSeq ++ s.encodings).getOrElse(Seq.empty)

  private def enc(name: String)(implicit p: Parameters): BitPat = {
    val e    = allEncodings
      .find(_.name == name)
      .getOrElse(throw new NoSuchElementException(s"Instruction '$name' not found in RV32IM"))
    val bits = (p(ILen) - 1 to 0 by -1).map { i =>
      val valueBit = (e.value >> i) & 1
      val maskBit  = (e.mask >> i) & 1
      if (maskBit == 1) valueBit.toString else "?"
    }.mkString

    BitPat("b" + bits)
  }

  override def utils: DecoderIsaImpl = new DecoderIsaImpl with Rv32imDecoderUopConsts {
    private val rv32i = DecoderRv32iIsa.utils

    override def value: String = "rv32im"

    override def default(implicit p: Parameters): List[BitPat] =
      rv32i.default

    override def table(implicit p: Parameters): Array[(BitPat, List[BitPat])] =
      rv32i.table ++ Array(
        enc("MUL")    -> List(Y, Y, Y, Y, Y, N, IMM_X, FU_MULT, UOP_MUL),
        enc("MULH")   -> List(Y, Y, Y, Y, Y, N, IMM_X, FU_MULT, UOP_MULH),
        enc("MULHSU") -> List(Y, Y, Y, Y, Y, N, IMM_X, FU_MULT, UOP_MULHSU),
        enc("MULHU")  -> List(Y, Y, Y, Y, Y, N, IMM_X, FU_MULT, UOP_MULHU),
        enc("DIV")    -> List(Y, Y, Y, Y, Y, N, IMM_X, FU_DIV, UOP_DIV),
        enc("DIVU")   -> List(Y, Y, Y, Y, Y, N, IMM_X, FU_DIV, UOP_DIVU),
        enc("REM")    -> List(Y, Y, Y, Y, Y, N, IMM_X, FU_DIV, UOP_REM),
        enc("REMU")   -> List(Y, Y, Y, Y, Y, N, IMM_X, FU_DIV, UOP_REMU)
      )
  }

  override def registry: NodeRegistry[DecoderIsaImpl] = DecoderIsaFactory
}
