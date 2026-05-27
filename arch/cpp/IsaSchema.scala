package arch.cpp

import arch.configs.{ ISA, Parameters }
import arch.isa.InstructionEncoding
import CppLiteral._
import CppValueDsl._

private[cpp] object CppIsaSchema {
  private val typeDecls: Seq[CppDecl] = Seq(
    StructDecl(
      "InstructionEncoding",
      Seq(
        "std::string_view" -> "name",
        "uint32_t"         -> "value",
        "uint32_t"         -> "mask",
      )
    )
  )

  def verilatedHeader(p: Parameters, options: CppCodegenOptions): String =
    topModuleName(p)
      .prependedAll("V")
      .appendedAll(".h")

  private def topModuleName(p: Parameters): String =
    s"${p(ISA).name}_system"

  private def verilatedClassName(p: Parameters): String =
    "V" + topModuleName(p)

  private def intType(width: Int): String =
    if (width <= 8) {
      "uint8_t"
    } else if (width <= 16) {
      "uint16_t"
    } else if (width <= 32) {
      "uint32_t"
    } else if (width <= 64) {
      "uint64_t"
    } else {
      throw new IllegalArgumentException(s"Unsupported generated C++ integer width: $width")
    }

  private def scalarFields: Seq[CppValue] =
    Seq(
      str("ISA_NAME", p => p(ISA).name),
      u32("XLEN", p => p(ISA).xlen),
      u32("ILEN", p => p(ISA).ilen),
      u32("NUM_ARCH_REGS", p => p(ISA).numArchRegs),
      bool("IS_BIG_ENDIAN", p => p(ISA).isBigEndian),
      u32("MICRO_OP_WIDTH", p => p(ISA).microOpWidth),
      u32("NUM_INSTR_ENCODINGS", p => p(ISA).instrSet.encodings.size),
    )

  private def typeAliases(options: CppCodegenOptions): Seq[CppValue] =
    Seq(
      TypeAliasValue("system_t", p => s"::${verilatedClassName(p)}"),
      TypeAliasValue("instr_t", p => intType(p(ISA).ilen)),
      TypeAliasValue("addr_t", p => intType(p(ISA).xlen)),
      TypeAliasValue("word_t", p => intType(p(ISA).xlen)),
      TypeAliasValue("half_t", _ => "uint16_t"),
      TypeAliasValue("byte_t", _ => "uint8_t"),
    )

  private val aggregateFields: Seq[CppValue] = Seq(
    struct(
      "InstructionEncoding",
      "ISA_NOP",
      p => {
        val nop = p(ISA).instrSet.nop.getOrElse(
          InstructionEncoding("", BigInt(0), BigInt(0))
        )

        renderInstructionFields(nop)
      }
    ),
    array(
      name = "INSTRUCTION_ENCODINGS",
      elemType = "InstructionEncoding",
      sizeName = "NUM_INSTR_ENCODINGS",
      values = p => p(ISA).instrSet.encodings.map(renderInstruction)
    ),
  )

  def emitTypes(w: CppWriter): Unit =
    typeDecls.foreach { decl =>
      decl.emit(w)
      w.line()
    }

  def emitValues(
    w: CppWriter,
    p: Parameters,
    options: CppCodegenOptions
  ): Unit = {
    typeAliases(options).foreach(_.emit(w, p))
    w.line()

    scalarFields.foreach(_.emit(w, p))
    w.line()

    aggregateFields.foreach { value =>
      value.emit(w, p)
      w.line()
    }
  }

  private def renderInstruction(e: InstructionEncoding): String =
    braced(renderInstructionFields(e))

  private def renderInstructionFields(e: InstructionEncoding): Seq[String] =
    Seq(
      cstrLit(e.name),
      hex32(e.value),
      hex32(e.mask),
    )
}
