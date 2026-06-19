package arch.isa.instructions.variants.riscv32

import arch.isa.instructions._

object Rv32im extends Riscv32IsaDefinition with Riscv32Base {
  override def name: String =
    "rv32im"

  override def features: Seq[String] =
    Rv32i.features ++ Seq("m")

  override def nop: Option[InstructionForm] =
    Rv32i.nop

  private def m(
    id: String,
    pattern: String,
    category: String,
  ): InstructionForm =
    rv(
      id = id,
      pattern = pattern,
      format = "r",
      asm = s"$name rd, rs1, rs2",
      fields = rFields,
      operands = Seq(rdW, rs1R, rs2R),
      semantic = sem(name, category, extension = "m")
    )

  override def forms: Seq[InstructionForm] =
    Rv32i.forms ++ Seq(
      m(
        "mul",
        "b0000001_?????_?????_000_?????_0110011",
        InstructionCategory.Mul,
      ),
      m(
        "mulh",
        "b0000001_?????_?????_001_?????_0110011",
        InstructionCategory.Mul,
      ),
      m(
        "mulhsu",
        "b0000001_?????_?????_010_?????_0110011",
        InstructionCategory.Mul,
      ),
      m(
        "mulhu",
        "b0000001_?????_?????_011_?????_0110011",
        InstructionCategory.Mul,
      ),
      m(
        "div",
        "b0000001_?????_?????_100_?????_0110011",
        InstructionCategory.Div,
      ),
      m(
        "divu",
        "b0000001_?????_?????_101_?????_0110011",
        InstructionCategory.Div,
      ),
      m(
        "rem",
        "b0000001_?????_?????_110_?????_0110011",
        InstructionCategory.Div,
      ),
      m(
        "remu",
        "b0000001_?????_?????_111_?????_0110011",
        InstructionCategory.Div,
      )
    )
}
