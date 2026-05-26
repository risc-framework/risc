package arch.isa

import chisel3.util.BitPat

object RV32IM extends IsaWrapper {
  override val isa: Isa = Isa(
    name = "rv32im",
    xlen = RV32I.xlen,
    ilen = RV32I.ilen,
    numArchRegs = RV32I.numArchRegs,
    microOpWidth = RV32I.microOpWidth,
    isBigEndian = RV32I.isBigEndian,
    instrSet = Some(
      InstructionSet(
        nop = RV32I.instrSet.nop,
        encodings = RV32I.instrSet.encodings ++ Seq(
          enc("MUL", BitPat("b0000001_?????_?????_000_?????_0110011")),
          enc("MULH", BitPat("b0000001_?????_?????_001_?????_0110011")),
          enc("MULHSU", BitPat("b0000001_?????_?????_010_?????_0110011")),
          enc("MULHU", BitPat("b0000001_?????_?????_011_?????_0110011")),
          enc("DIV", BitPat("b0000001_?????_?????_100_?????_0110011")),
          enc("DIVU", BitPat("b0000001_?????_?????_101_?????_0110011")),
          enc("REM", BitPat("b0000001_?????_?????_110_?????_0110011")),
          enc("REMU", BitPat("b0000001_?????_?????_111_?????_0110011")),
        )
      )
    ),
  )

  private def enc(name: String, bp: BitPat): InstructionEncoding =
    InstructionEncoding(
      name = name,
      value = bp.value.intValue,
      mask = bp.mask.intValue,
    )

  IsaFactory.register(this)
}
