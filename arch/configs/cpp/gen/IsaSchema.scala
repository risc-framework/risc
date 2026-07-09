package arch.configs.cpp.gen

import arch.configs.{ ISA, TopModuleName, Parameters }
import arch.configs.cpp.CppCodegenOptions
import arch.configs.cpp.dsl.{ CppDecl, CppValue, CppWriter, StructDecl, TypeAliasValue }
import arch.configs.cpp.dsl.CppLiteral._
import arch.configs.cpp.dsl.CppValueDsl._
import arch.isa._
import arch.isa.instructions._

private[cpp] object CppIsaSchema {
  final private val MaxPatternBytes          = 16
  final private val MaxInstructionFields     = 16
  final private val MaxInstructionOperands   = 16
  final private val MaxOperandPieces         = 8
  final private val MaxInstructionAttributes = 16

  private val typeDecls: Seq[CppDecl] = Seq(
    StructDecl(
      "IsaMode",
      Seq(
        "std::string_view" -> "name",
        "uint16_t"         -> "min_bits",
        "uint16_t"         -> "max_bits",
        "std::string_view" -> "endianness",
        "uint16_t"         -> "alignment_bytes",
        "std::string_view" -> "decoder"
      )
    ),
    StructDecl(
      "InstructionField",
      Seq(
        "std::string_view" -> "name",
        "uint8_t"          -> "hi",
        "uint8_t"          -> "lo",
        "bool"             -> "is_signed",
        "std::string_view" -> "role"
      )
    ),
    StructDecl(
      "OperandPiece",
      Seq(
        "uint8_t" -> "src_hi",
        "uint8_t" -> "src_lo",
        "uint8_t" -> "dst_lo"
      )
    ),
    StructDecl(
      "InstructionOperand",
      Seq(
        "std::string_view"                             -> "name",
        "std::string_view"                             -> "kind",
        "std::string_view"                             -> "access",
        "uint16_t"                                     -> "width",
        "bool"                                         -> "is_signed",
        s"std::array<OperandPiece, $MaxOperandPieces>" -> "pieces",
        "uint8_t"                                      -> "num_pieces",
        "std::string_view"                             -> "extractor",
        "std::string_view"                             -> "display",
        "std::string_view"                             -> "implicit_value"
      )
    ),
    StructDecl(
      "InstructionForm",
      Seq(
        "std::string_view"                                         -> "id",
        "std::string_view"                                         -> "encoding_kind",
        "uint64_t"                                                 -> "value",
        "uint64_t"                                                 -> "mask",
        "uint16_t"                                                 -> "min_bits",
        "uint16_t"                                                 -> "max_bits",
        "uint16_t"                                                 -> "fixed_bits",
        s"std::array<uint8_t, $MaxPatternBytes>"                   -> "byte_value",
        s"std::array<uint8_t, $MaxPatternBytes>"                   -> "byte_mask",
        "uint8_t"                                                  -> "num_pattern_bytes",
        "std::string_view"                                         -> "decoder",
        "std::string_view"                                         -> "decoder_key",
        "std::string_view"                                         -> "mode",
        "std::string_view"                                         -> "format",
        "std::string_view"                                         -> "asm_template",
        "std::string_view"                                         -> "semantic_id",
        "std::string_view"                                         -> "category",
        "std::string_view"                                         -> "extension",
        "std::string_view"                                         -> "privilege",
        "std::string_view"                                         -> "control_flow",
        "std::string_view"                                         -> "memory",
        "bool"                                                     -> "reads_pc",
        "bool"                                                     -> "writes_pc",
        "bool"                                                     -> "may_trap",
        "bool"                                                     -> "serializing",
        "std::string_view"                                         -> "notes",
        s"std::array<std::string_view, $MaxInstructionAttributes>" -> "attributes",
        "uint8_t"                                                  -> "num_attributes",
        "std::string_view"                                         -> "alias_of",
        "int32_t"                                                  -> "priority",
        s"std::array<InstructionField, $MaxInstructionFields>"     -> "fields",
        "uint8_t"                                                  -> "num_fields",
        s"std::array<InstructionOperand, $MaxInstructionOperands>" -> "operands",
        "uint8_t"                                                  -> "num_operands"
      )
    )
  )

  def verilatedHeader(p: Parameters, options: CppCodegenOptions): String =
    topModuleName(p).prependedAll("V").appendedAll(".h")

  private def topModuleName(p: Parameters): String =
    p(TopModuleName)

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
      throw new IllegalArgumentException(s"Unsupported generated C++ scalar integer width: $width")
    }

  private def instrScalarType(width: Int): String =
    if (width <= 64) intType(width) else "uint64_t"

  private def ceilDiv(x: Int, y: Int): Int = {
    require(x >= 0, s"ceilDiv numerator must be non-negative, got $x")
    require(y > 0, s"ceilDiv denominator must be positive, got $y")
    (x + y - 1) / y
  }

  private def scalarFields: Seq[CppValue] =
    Seq(
      str("ISA_NAME", p => p(ISA).name),
      str("ISA_FAMILY", p => p(ISA).family),
      str("DEFAULT_ISA_MODE", p => p(ISA).defaultMode.name),
      u32("XLEN", p => p(ISA).xlen),
      u32("ILEN", p => p(ISA).ilen),
      u32("NUM_ARCH_REGS", p => p(ISA).numArchRegs),
      bool("IS_BIG_ENDIAN", p => p(ISA).isBigEndian),
      bool("HAS_FIXED_INSTRUCTION_WIDTH", p => p(ISA).hasFixedInstructionWidth),
      u32("MIN_INSTR_BITS", p => p(ISA).minInstrBits),
      u32("MAX_INSTR_BITS", p => p(ISA).maxInstrBits),
      u32("MAX_INSTR_BYTES", p => ceilDiv(p(ISA).maxInstrBits, 8)),
      u32("NUM_ISA_MODES", p => p(ISA).modes.size),
      u32("NUM_INSTR_FORMS", p => p(ISA).instrSet.forms.size),
      u32("MAX_PATTERN_BYTES", _ => MaxPatternBytes),
      u32("MAX_INSTRUCTION_FIELDS", _ => MaxInstructionFields),
      u32("MAX_INSTRUCTION_OPERANDS", _ => MaxInstructionOperands),
      u32("MAX_OPERAND_PIECES", _ => MaxOperandPieces),
      u32("MAX_INSTRUCTION_ATTRIBUTES", _ => MaxInstructionAttributes)
    )

  private def typeAliases(options: CppCodegenOptions): Seq[CppValue] =
    Seq(
      TypeAliasValue("soc_t", p => s"::${verilatedClassName(p)}"),
      TypeAliasValue("instr_t", p => instrScalarType(p(ISA).maxInstrBits)),
      TypeAliasValue("addr_t", p => intType(p(ISA).xlen)),
      TypeAliasValue("word_t", p => intType(p(ISA).xlen)),
      TypeAliasValue("byte_t", _ => "uint8_t")
    )

  private val aggregateFields: Seq[CppValue] = Seq(
    array(
      name = "ISA_MODES",
      elemType = "IsaMode",
      sizeName = "NUM_ISA_MODES",
      values = p => p(ISA).modes.map(renderMode)
    ),
    struct(
      "InstructionForm",
      "ISA_NOP",
      p => p(ISA).instrSet.nop.map(renderFormFields).getOrElse(renderEmptyFormFields)
    ),
    array(
      name = "INSTRUCTION_FORMS",
      elemType = "InstructionForm",
      sizeName = "NUM_INSTR_FORMS",
      values = p => p(ISA).instrSet.forms.map(renderForm)
    )
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

  private def renderMode(mode: IsaMode): String =
    braced(
      Seq(
        cstrLit(mode.name),
        u16Lit(mode.minInstrBits),
        u16Lit(mode.maxInstrBits),
        cstrLit(mode.endianness),
        u16Lit(mode.alignmentBytes),
        cstrLit(mode.decoder)
      )
    )

  private def renderForm(form: InstructionForm): String =
    braced(renderFormFields(form))

  private def renderFormFields(form: InstructionForm): Seq[String] = {
    val pattern  = renderPatternFields(form.pattern)
    val semantic = form.semantic

    Seq(
      cstrLit(form.id),
    ) ++ pattern ++ Seq(
      cstrLit(form.mode),
      cstrLit(form.format),
      cstrLit(form.asm),
      cstrLit(semantic.id),
      cstrLit(semantic.category),
      cstrLit(semantic.extension),
      cstrLit(semantic.privilege),
      cstrLit(semantic.controlFlow),
      cstrLit(semantic.memory),
      boolLit(semantic.readsPc),
      boolLit(semantic.writesPc),
      boolLit(semantic.mayTrap),
      boolLit(semantic.serializing),
      cstrLit(semantic.notes),
      renderStringArray(form.attributes, MaxInstructionAttributes),
      u8Lit(form.attributes.size),
      cstrLit(form.aliasOf.getOrElse("")),
      i32Lit(form.priority),
      renderFieldArray(form.fields),
      u8Lit(form.fields.size),
      renderOperandArray(form.operands),
      u8Lit(form.operands.size)
    )
  }

  private def renderEmptyFormFields: Seq[String] =
    Seq(
      cstrLit(""),
      cstrLit(""),
      cstrLit("none"),
      u64Hex(BigInt(0)),
      u64Hex(BigInt(0)),
      u16Lit(0),
      u16Lit(0),
      u16Lit(0),
      renderByteArray(Seq.empty),
      renderByteArray(Seq.empty),
      u8Lit(0),
      cstrLit(""),
      cstrLit(""),
      cstrLit(""),
      cstrLit(""),
      cstrLit(""),
      cstrLit("unknown"),
      cstrLit(InstructionCategory.Unknown),
      cstrLit(""),
      cstrLit(PrivilegeLevel.Any),
      cstrLit(ControlFlowKind.None),
      cstrLit(MemoryAccessKind.None),
      boolLit(false),
      boolLit(false),
      boolLit(false),
      boolLit(false),
      cstrLit(""),
      cstrLit(""),
      renderStringArray(Seq.empty, MaxInstructionAttributes),
      u8Lit(0),
      cstrLit(""),
      i32Lit(0),
      renderFieldArray(Seq.empty),
      u8Lit(0),
      renderOperandArray(Seq.empty),
      u8Lit(0)
    )

  private def renderPatternFields(pattern: EncodingPattern): Seq[String] =
    pattern match {
      case p: FixedBitPattern =>
        require(
          p.width <= 64,
          s"fixed-bit C++ schema only supports up to 64-bit scalar patterns, got ${p.width}"
        )
        Seq(
          cstrLit(p.kind),
          u64Hex(p.value),
          u64Hex(p.mask),
          u16Lit(p.minBits),
          u16Lit(p.maxBits),
          u16Lit(p.width),
          renderByteArray(Seq.empty),
          renderByteArray(Seq.empty),
          u8Lit(0),
          cstrLit(""),
          cstrLit("")
        )

      case p: BytePattern =>
        require(
          p.value.size <= MaxPatternBytes,
          s"byte pattern has ${p.value.size} bytes, max supported is $MaxPatternBytes"
        )
        Seq(
          cstrLit(p.kind),
          u64Hex(BigInt(0)),
          u64Hex(BigInt(0)),
          u16Lit(p.minBits),
          u16Lit(p.maxBits),
          u16Lit(p.fixedWidth.getOrElse(0)),
          renderByteArray(p.value),
          renderByteArray(p.mask),
          u8Lit(p.value.size),
          cstrLit(""),
          cstrLit("")
        )

      case p: DecoderPattern =>
        Seq(
          cstrLit(p.kind),
          u64Hex(BigInt(0)),
          u64Hex(BigInt(0)),
          u16Lit(p.minBits),
          u16Lit(p.maxBits),
          u16Lit(p.fixedWidth.getOrElse(0)),
          renderByteArray(Seq.empty),
          renderByteArray(Seq.empty),
          u8Lit(0),
          cstrLit(p.decoder),
          cstrLit(p.key)
        )
    }

  private def renderFieldArray(fields: Seq[InstructionField]): String = {
    require(
      fields.size <= MaxInstructionFields,
      s"too many instruction fields: ${fields.size}, max $MaxInstructionFields"
    )
    arrayLiteral(
      values = fields,
      maxSize = MaxInstructionFields,
      render = renderField,
      empty = renderEmptyField
    )
  }

  private def renderField(field: InstructionField): String =
    braced(
      Seq(
        cstrLit(field.name),
        u8Lit(field.hi),
        u8Lit(field.lo),
        boolLit(field.signed),
        cstrLit(field.role)
      )
    )

  private def renderEmptyField: String =
    braced(
      Seq(
        cstrLit(""),
        u8Lit(0),
        u8Lit(0),
        boolLit(false),
        cstrLit("")
      )
    )

  private def renderOperandArray(operands: Seq[InstructionOperand]): String = {
    require(
      operands.size <= MaxInstructionOperands,
      s"too many instruction operands: ${operands.size}, max $MaxInstructionOperands"
    )
    arrayLiteral(
      values = operands,
      maxSize = MaxInstructionOperands,
      render = renderOperand,
      empty = renderEmptyOperand
    )
  }

  private def renderOperand(operand: InstructionOperand): String = {
    require(
      operand.pieces.size <= MaxOperandPieces,
      s"operand '${operand.name}' has ${operand.pieces.size} pieces, max $MaxOperandPieces"
    )

    braced(
      Seq(
        cstrLit(operand.name),
        cstrLit(operand.kind),
        cstrLit(operand.access),
        u16Lit(operand.width),
        boolLit(operand.signed),
        renderOperandPieceArray(operand.pieces),
        u8Lit(operand.pieces.size),
        cstrLit(operand.extractor),
        cstrLit(operand.display),
        cstrLit(operand.implicitValue)
      )
    )
  }

  private def renderEmptyOperand: String =
    braced(
      Seq(
        cstrLit(""),
        cstrLit(OperandKind.Unknown),
        cstrLit(OperandAccess.None),
        u16Lit(0),
        boolLit(false),
        renderOperandPieceArray(Seq.empty),
        u8Lit(0),
        cstrLit(""),
        cstrLit(""),
        cstrLit("")
      )
    )

  private def renderOperandPieceArray(pieces: Seq[OperandPiece]): String = {
    require(
      pieces.size <= MaxOperandPieces,
      s"too many operand pieces: ${pieces.size}, max $MaxOperandPieces"
    )
    arrayLiteral(
      values = pieces,
      maxSize = MaxOperandPieces,
      render = renderOperandPiece,
      empty = renderEmptyOperandPiece
    )
  }

  private def renderOperandPiece(piece: OperandPiece): String =
    braced(
      Seq(
        u8Lit(piece.srcHi),
        u8Lit(piece.srcLo),
        u8Lit(piece.dstLo)
      )
    )

  private def renderEmptyOperandPiece: String =
    braced(
      Seq(
        u8Lit(0),
        u8Lit(0),
        u8Lit(0)
      )
    )

  private def renderByteArray(values: Seq[Int]): String = {
    require(
      values.size <= MaxPatternBytes,
      s"byte array has ${values.size} values, max $MaxPatternBytes"
    )
    arrayLiteral(
      values = values,
      maxSize = MaxPatternBytes,
      render = u8Lit,
      empty = u8Lit(0)
    )
  }

  private def renderStringArray(values: Seq[String], maxSize: Int): String =
    arrayLiteral(
      values = values,
      maxSize = maxSize,
      render = cstrLit,
      empty = cstrLit("")
    )

  private def arrayLiteral[T](
    values: Seq[T],
    maxSize: Int,
    render: T => String,
    empty: String
  ): String = {
    require(values.size <= maxSize, s"array literal has ${values.size} values, max $maxSize")
    val rendered = values.map(render) ++ Seq.fill(maxSize - values.size)(empty)
    s"{{${rendered.mkString(", ")}}}"
  }

  private def boolLit(value: Boolean): String =
    if (value) "true" else "false"

  private def u8Lit(value: Int): String = {
    require(value >= 0 && value <= 0xff, s"uint8_t literal out of range: $value")
    s"${value}u"
  }

  private def u16Lit(value: Int): String = {
    require(value >= 0 && value <= 0xffff, s"uint16_t literal out of range: $value")
    s"${value}u"
  }

  private def i32Lit(value: Int): String =
    value.toString

  private def u64Hex(value: BigInt): String = {
    val max    = (BigInt(1) << 64) - 1
    require(value >= 0 && value <= max, s"uint64_t hex literal out of range: $value")
    val raw    = value.toString(16)
    val padded = "0" * (16 - raw.length) + raw
    s"0x${padded}ULL"
  }
}
