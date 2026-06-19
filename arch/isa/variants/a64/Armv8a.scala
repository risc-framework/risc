package arch.isa.variants.a64

import arch.isa._

object Armv8a extends A64IsaDefinition with A64Base {
  override def name: String =
    "armv8a"

  override def features: Seq[String] =
    Seq("a64", "aarch64", "armv8-a", "base-arith")

  override def nop: Option[InstructionForm] =
    Some(
      a64(
        id = "nop",
        pattern = "b11010101000000110010000000011111",
        format = "hint",
        asm = "nop",
        fields = hintFields,
        operands = Seq.empty,
        semantic = sem("nop", InstructionCategory.System),
        attributes = Seq("hint"),
        description = "A64 NOP hint instruction"
      )
    )

  override def forms: Seq[InstructionForm] =
    Seq(
      a64(
        id = "add",
        pattern = "b1_0_0_01011_??_0_?????_??????_?????_?????",
        format = "addsub_shift",
        asm = "add xd, xn, xm, shift #shamt",
        fields = addSubShiftFields,
        operands = Seq(rdW, rnR, rmR, shiftR, shamtR),
        semantic = sem("add", InstructionCategory.Alu)
      ),
      a64(
        id = "sub",
        pattern = "b1_1_0_01011_??_0_?????_??????_?????_?????",
        format = "addsub_shift",
        asm = "sub xd, xn, xm, shift #shamt",
        fields = addSubShiftFields,
        operands = Seq(rdW, rnR, rmR, shiftR, shamtR),
        semantic = sem("sub", InstructionCategory.Alu)
      ),
      a64(
        id = "adds",
        pattern = "b1_0_1_01011_??_0_?????_??????_?????_?????",
        format = "addsub_shift",
        asm = "adds xd, xn, xm, shift #shamt",
        fields = addSubShiftFields,
        operands = Seq(rdW, rnR, rmR, shiftR, shamtR),
        semantic = sem("adds", InstructionCategory.Alu, notes = "sets NZCV flags")
      ),
      a64(
        id = "subs",
        pattern = "b1_1_1_01011_??_0_?????_??????_?????_?????",
        format = "addsub_shift",
        asm = "subs xd, xn, xm, shift #shamt",
        fields = addSubShiftFields,
        operands = Seq(rdW, rnR, rmR, shiftR, shamtR),
        semantic = sem("subs", InstructionCategory.Alu, notes = "sets NZCV flags")
      ),
      a64(
        id = "addi",
        pattern = "b1_0_0_10001_0_?_????????????_?????_?????",
        format = "addsub_imm",
        asm = "add xd, xn, #imm12{, lsl #12}",
        fields = addSubImmFields,
        operands = Seq(rdW, rnR, imm12R, imm12ShiftR),
        semantic = sem("addi", InstructionCategory.Alu)
      ),
      a64(
        id = "subi",
        pattern = "b1_1_0_10001_0_?_????????????_?????_?????",
        format = "addsub_imm",
        asm = "sub xd, xn, #imm12{, lsl #12}",
        fields = addSubImmFields,
        operands = Seq(rdW, rnR, imm12R, imm12ShiftR),
        semantic = sem("subi", InstructionCategory.Alu)
      ),
      a64(
        id = "addsi",
        pattern = "b1_0_1_10001_0_?_????????????_?????_?????",
        format = "addsub_imm",
        asm = "adds xd, xn, #imm12{, lsl #12}",
        fields = addSubImmFields,
        operands = Seq(rdW, rnR, imm12R, imm12ShiftR),
        semantic = sem("addsi", InstructionCategory.Alu, notes = "sets NZCV flags")
      ),
      a64(
        id = "subsi",
        pattern = "b1_1_1_10001_0_?_????????????_?????_?????",
        format = "addsub_imm",
        asm = "subs xd, xn, #imm12{, lsl #12}",
        fields = addSubImmFields,
        operands = Seq(rdW, rnR, imm12R, imm12ShiftR),
        semantic = sem("subsi", InstructionCategory.Alu, notes = "sets NZCV flags")
      ),
      a64(
        id = "and",
        pattern = "b1_00_01010_??_0_?????_??????_?????_?????",
        format = "logic_shift",
        asm = "and xd, xn, xm, shift #shamt",
        fields = logicalShiftFields,
        operands = Seq(rdW, rnR, rmR, shiftR, shamtR),
        semantic = sem("and", InstructionCategory.Alu)
      ),
      a64(
        id = "orr",
        pattern = "b1_01_01010_??_0_?????_??????_?????_?????",
        format = "logic_shift",
        asm = "orr xd, xn, xm, shift #shamt",
        fields = logicalShiftFields,
        operands = Seq(rdW, rnR, rmR, shiftR, shamtR),
        semantic = sem("orr", InstructionCategory.Alu)
      ),
      a64(
        id = "eor",
        pattern = "b1_10_01010_??_0_?????_??????_?????_?????",
        format = "logic_shift",
        asm = "eor xd, xn, xm, shift #shamt",
        fields = logicalShiftFields,
        operands = Seq(rdW, rnR, rmR, shiftR, shamtR),
        semantic = sem("eor", InstructionCategory.Alu)
      ),
      a64(
        id = "ands",
        pattern = "b1_11_01010_??_0_?????_??????_?????_?????",
        format = "logic_shift",
        asm = "ands xd, xn, xm, shift #shamt",
        fields = logicalShiftFields,
        operands = Seq(rdW, rnR, rmR, shiftR, shamtR),
        semantic = sem("ands", InstructionCategory.Alu, notes = "sets NZCV flags")
      ),
      a64(
        id = "movn",
        pattern = "b1_00_100101_??_????????????????_?????",
        format = "move_wide",
        asm = "movn xd, #imm16, lsl #(hw * 16)",
        fields = moveWideFields,
        operands = Seq(rdW, imm16R, hwR),
        semantic = sem("movn", InstructionCategory.Alu)
      ),
      a64(
        id = "movz",
        pattern = "b1_10_100101_??_????????????????_?????",
        format = "move_wide",
        asm = "movz xd, #imm16, lsl #(hw * 16)",
        fields = moveWideFields,
        operands = Seq(rdW, imm16R, hwR),
        semantic = sem("movz", InstructionCategory.Alu)
      ),
      a64(
        id = "movk",
        pattern = "b1_11_100101_??_????????????????_?????",
        format = "move_wide",
        asm = "movk xd, #imm16, lsl #(hw * 16)",
        fields = moveWideFields,
        operands = Seq(rdW, imm16R, hwR),
        semantic = sem("movk", InstructionCategory.Alu)
      )
    )
}
