package arch.configs

import arch.isa._
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path, Paths }
import vcache.repl._

object CppCodegen {
  final case class Options(
    baseDir: Path = Paths.get("build"),
    configNamespace: String = "demu::sys_def",
    isaNamespace: String = "demu::isa_def",
    isaInclude: String = "isa_def.hh",
    emitMacros: Boolean = true
  )

  final case class OutputPaths(
    configHeader: Path,
    isaHeader: Path
  )

  def emit(p: Parameters, configHeader: String, isaHeader: String): Unit =
    emit(
      p,
      OutputPaths(
        configHeader = Paths.get(configHeader),
        isaHeader = Paths.get(isaHeader)
      ),
      Options()
    )

  def emit(
    p: Parameters,
    configHeader: String,
    isaHeader: String,
    options: Options
  ): Unit =
    emit(
      p,
      OutputPaths(
        configHeader = Paths.get(configHeader),
        isaHeader = Paths.get(isaHeader)
      ),
      options
    )

  def emit(
    p: Parameters,
    paths: OutputPaths,
    options: Options = Options()
  ): Unit = {
    val isaPath    = resolveOutputPath(options, paths.isaHeader)
    val configPath = resolveOutputPath(options, paths.configHeader)

    write(isaPath, renderIsaHeader(p, options))
    write(configPath, renderConfigHeader(p, options))

    println(s"[CppCodegen] generated ISA header    -> $isaPath")
    println(s"[CppCodegen] generated config header -> $configPath")
  }

  def emitIsa(p: Parameters, isaHeader: String): Unit =
    emitIsa(p, Paths.get(isaHeader), Options())

  def emitIsa(
    p: Parameters,
    isaHeader: Path,
    options: Options = Options()
  ): Unit = {
    val path = resolveOutputPath(options, isaHeader)
    write(path, renderIsaHeader(p, options))
    println(s"[CppCodegen] generated ISA header -> $path")
  }

  def emitConfig(p: Parameters, configHeader: String): Unit =
    emitConfig(p, Paths.get(configHeader), Options())

  def emitConfig(
    p: Parameters,
    configHeader: Path,
    options: Options = Options()
  ): Unit = {
    val path = resolveOutputPath(options, configHeader)
    write(path, renderConfigHeader(p, options))
    println(s"[CppCodegen] generated config header -> $path")
  }

  private def resolveOutputPath(options: Options, path: Path): Path =
    if (path.isAbsolute) {
      path.normalize()
    } else {
      options.baseDir.resolve(path).normalize()
    }

  private def write(path: Path, text: String): Unit = {
    val parent = path.getParent
    if (parent != null) {
      Files.createDirectories(parent)
    }

    Files.write(path, text.getBytes(StandardCharsets.UTF_8))
  }

  def renderIsaHeader(p: Parameters, options: Options = Options()): String = {
    val w = new Writer

    w.line("#pragma once")
    w.line()
    w.line("#include <array>")
    w.line("#include <cstdint>")
    w.line("#include <string_view>")
    w.line()

    w.namespace(options.isaNamespace) {
      IsaSchema.emitTypes(w)
      IsaSchema.emitValues(w, p)
    }

    if (options.emitMacros) {
      w.line()
      IsaSchema.emitMacros(w, p)
    }

    w.result
  }

  def renderConfigHeader(p: Parameters, options: Options = Options()): String = {
    val w = new Writer

    w.line("#pragma once")
    w.line()
    w.line("#include <array>")
    w.line("#include <cstdint>")
    w.line("#include <string_view>")
    w.line(s"""#include "${options.isaInclude}"""")
    w.line()

    w.namespace(options.configNamespace) {
      ConfigSchema.emitTypes(w)
      ConfigSchema.emitValues(w, p, options)
    }

    if (options.emitMacros) {
      w.line()
      ConfigSchema.emitMacros(w, p)
    }

    w.result
  }

  private object IsaSchema {
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

    private val scalarFields: Seq[CppValue] = Seq(
      str("ISA_NAME", p => p(ISA).name),
      u32("XLEN", p => p(ISA).xlen),
      u32("ILEN", p => p(ISA).ilen),
      u32("NUM_ARCH_REGS", p => p(ISA).numArchRegs),
      bool("IS_BIG_ENDIAN", p => p(ISA).isBigEndian),
      u32("MICRO_OP_WIDTH", p => p(ISA).microOpWidth),
      u32("NUM_INSTR_ENCODINGS", p => p(ISA).instrSet.encodings.size),
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

    def emitTypes(w: Writer): Unit =
      typeDecls.foreach { decl =>
        decl.emit(w)
        w.line()
      }

    def emitValues(w: Writer, p: Parameters): Unit = {
      scalarFields.foreach(_.emit(w, p))
      w.line()

      aggregateFields.foreach { value =>
        value.emit(w, p)
        w.line()
      }
    }

    def emitMacros(w: Writer, p: Parameters): Unit = {
      val isaMacro = "__ISA_" + macroIdent(p(ISA).name) + "__"

      val macros = Seq(
        isaMacro                      -> "1",
        "RTL_ISA_NAME"                -> strLit(p(ISA).name),
        "RTL_ISA_XLEN"                -> p(ISA).xlen.toString,
        "RTL_ISA_ILEN"                -> p(ISA).ilen.toString,
        "RTL_ISA_NUM_ARCH_REGS"       -> p(ISA).numArchRegs.toString,
        "RTL_ISA_IS_BIG_ENDIAN"       -> (if (p(ISA).isBigEndian) "1" else "0"),
        "RTL_ISA_MICRO_OP_WIDTH"      -> p(ISA).microOpWidth.toString,
        "RTL_ISA_NUM_INSTR_ENCODINGS" -> p(ISA).instrSet.encodings.size.toString,
      )

      emitMacroGuards(w, macros)
    }

    private def renderInstruction(e: InstructionEncoding): String =
      braced(renderInstructionFields(e))

    private def renderInstructionFields(e: InstructionEncoding): Seq[String] =
      Seq(
        strLit(e.name),
        hex32(e.value),
        hex32(e.mask),
      )
  }

  private object ConfigSchema {
    private val typeDecls: Seq[CppDecl] = Seq(
      EnumDecl(
        "FunctionalUnitType",
        "uint8_t",
        Seq(
          "UNKNOWN" -> 0,
          "ALU"     -> 1,
          "MULT"    -> 2,
          "DIV"     -> 3,
          "LD"      -> 4,
          "ST"      -> 5,
          "BRU"     -> 6,
          "CSR"     -> 7,
        )
      ),
      EnumDecl(
        "DeviceType",
        "uint8_t",
        Seq(
          "UNKNOWN" -> 0,
          "SRAM"    -> 1,
          "UART"    -> 2,
          "IRH"     -> 3,
        )
      ),
      EnumDecl(
        "ReplPolicy",
        "uint8_t",
        Seq(
          "UNKNOWN"    -> 0,
          "RANDOM"     -> 1,
          "FIFO"       -> 2,
          "LFU"        -> 3,
          "LRU"        -> 4,
          "PSEUDO_LRU" -> 5,
        )
      ),
      EnumDecl(
        "BusType",
        "uint8_t",
        Seq(
          "UNKNOWN" -> 0,
          "AXIL"    -> 1,
          "AXIF"    -> 2,
        )
      ),
      StructDecl(
        "FunctionalUnitDescriptor",
        Seq(
          "std::string_view"   -> "name",
          "FunctionalUnitType" -> "type",
        )
      ),
      StructDecl(
        "DeviceDescriptor",
        Seq(
          "std::string_view" -> "name",
          "DeviceType"       -> "type",
          "uint64_t"         -> "base",
          "uint64_t"         -> "size",
        )
      ),
      StructDecl(
        "CacheConfig",
        Seq(
          "uint32_t"   -> "sets",
          "uint32_t"   -> "ways",
          "uint32_t"   -> "line_size",
          "ReplPolicy" -> "repl_policy",
        )
      ),
    )

    private val scalarFields: Seq[CppValue] = Seq(
      u64("FREQ", Frequency),
      bool("ENABLE_DEBUG", EnableDebug),
      alias("std::string_view", "ISA_NAME", "demu::isa_def::ISA_NAME"),
      alias("uint32_t", "XLEN", "demu::isa_def::XLEN"),
      alias("uint32_t", "ILEN", "demu::isa_def::ILEN"),
      alias("uint32_t", "NUM_ARCH_REGS", "demu::isa_def::NUM_ARCH_REGS"),
      alias("uint32_t", "MICRO_OP_WIDTH", "demu::isa_def::MICRO_OP_WIDTH"),
      u32("BYTES_PER_WORD", BytesPerWord),
      u32("BYTES_PER_INSTR", BytesPerInstr),
      u32("PC_STEP", PCStep),
      u64("RESET_VECTOR", ResetVector),
      u32("IBUFFER_SIZE", IBufferSize),
      u32("ISSUE_WIDTH", IssueWidth),
      bool("REGFILE_USE_BYPASS", IsRegfileUseBypass),
      u32("NUM_PHY_REGS", NumPhyRegs),
      u32("NUM_FUS", NumFUs),
      u32("NUM_LDS", NumLDs),
      u32("FU_TYPE_WIDTH", FuTypeWidth),
      u32("FU_ID_WIDTH", FuIdWidth),
      u32("MULT_PIPELINE_STAGES", MultPipelineStages),
      u32("ROB_SIZE", RobSize),
      u32("ROB_TAG_WIDTH", RobTagWidth),
      u32("STORE_BUFFER_SIZE", StoreBufferSize),
      u32("BTB_SETS", BTBSets),
      u32("BTB_WAYS", BTBWays),
      cppEnum("ReplPolicy", "BTB_REPL_POLICY", p => replCpp(p(BTBReplPolicy))),
      u32("GSHARE_GHR_WIDTH", GShareGhrWidth),
      cppEnum("BusType", "BUS_TYPE", p => busCpp(p(BusType))),
      u32("BUS_CROSSBAR_FIFO_DEPTH_PER_CLIENT", BusCrossbarFifoDepthPerClient),
      u32("NUM_BUS_DEVICES", p => p(BusAddressMap).size),
    )

    private val aggregateFields: Seq[CppValue] = Seq(
      struct(
        "CacheConfig",
        "L1I",
        p =>
          Seq(
            u32Lit(p(L1ICacheSets)),
            u32Lit(p(L1ICacheWays)),
            u32Lit(p(L1ICacheLineSize)),
            enumLit("ReplPolicy", replCpp(p(L1ICacheReplPolicy))),
          )
      ),
      struct(
        "CacheConfig",
        "L1D",
        p =>
          Seq(
            u32Lit(p(L1DCacheSets)),
            u32Lit(p(L1DCacheWays)),
            u32Lit(p(L1DCacheLineSize)),
            enumLit("ReplPolicy", replCpp(p(L1DCacheReplPolicy))),
          )
      ),
      array(
        name = "FUNCTIONAL_UNITS",
        elemType = "FunctionalUnitDescriptor",
        sizeName = "NUM_FUS",
        values = p => p(FunctionalUnits).map(renderFu)
      ),
      array(
        name = "BUS_ADDRESS_MAP",
        elemType = "DeviceDescriptor",
        sizeName = "NUM_BUS_DEVICES",
        values = p => p(BusAddressMap).map(renderDevice)
      ),
    )

    def emitTypes(w: Writer): Unit =
      typeDecls.foreach { decl =>
        decl.emit(w)
        w.line()
      }

    def emitValues(w: Writer, p: Parameters, options: Options): Unit = {
      scalarFields.foreach {
        case AliasValue(tpe, name, expr) =>
          val fixedExpr = expr.replace("demu::isa_def", options.isaNamespace)
          ScalarValue(tpe, name, _ => fixedExpr).emit(w, p)

        case value =>
          value.emit(w, p)
      }

      w.line()

      aggregateFields.foreach { value =>
        value.emit(w, p)
        w.line()
      }
    }

    def emitMacros(w: Writer, p: Parameters): Unit = {
      val macros = Seq(
        "RTL_CONFIG_FREQ"                               -> s"${p(Frequency)}ull",
        "RTL_CONFIG_ENABLE_DEBUG"                       -> (if (p(EnableDebug)) "1" else "0"),
        "RTL_CONFIG_RESET_VECTOR"                       -> hex64(p(ResetVector)),
        "RTL_CONFIG_IBUFFER_SIZE"                       -> p(IBufferSize).toString,
        "RTL_CONFIG_ISSUE_WIDTH"                        -> p(IssueWidth).toString,
        "RTL_CONFIG_REGFILE_USE_BYPASS"                 -> (if (p(IsRegfileUseBypass)) "1" else "0"),
        "RTL_CONFIG_NUM_PHY_REGS"                       -> p(NumPhyRegs).toString,
        "RTL_CONFIG_NUM_FUS"                            -> p(NumFUs).toString,
        "RTL_CONFIG_NUM_LDS"                            -> p(NumLDs).toString,
        "RTL_CONFIG_MULT_PIPELINE_STAGES"               -> p(MultPipelineStages).toString,
        "RTL_CONFIG_ROB_SIZE"                           -> p(RobSize).toString,
        "RTL_CONFIG_ROB_TAG_WIDTH"                      -> p(RobTagWidth).toString,
        "RTL_CONFIG_STORE_BUFFER_SIZE"                  -> p(StoreBufferSize).toString,
        "RTL_CONFIG_BTB_SETS"                           -> p(BTBSets).toString,
        "RTL_CONFIG_BTB_WAYS"                           -> p(BTBWays).toString,
        "RTL_CONFIG_GSHARE_GHR_WIDTH"                   -> p(GShareGhrWidth).toString,
        "RTL_CONFIG_L1I_SETS"                           -> p(L1ICacheSets).toString,
        "RTL_CONFIG_L1I_WAYS"                           -> p(L1ICacheWays).toString,
        "RTL_CONFIG_L1I_LINE_SIZE"                      -> p(L1ICacheLineSize).toString,
        "RTL_CONFIG_L1D_SETS"                           -> p(L1DCacheSets).toString,
        "RTL_CONFIG_L1D_WAYS"                           -> p(L1DCacheWays).toString,
        "RTL_CONFIG_L1D_LINE_SIZE"                      -> p(L1DCacheLineSize).toString,
        "RTL_CONFIG_BUS_CROSSBAR_FIFO_DEPTH_PER_CLIENT" ->
          p(BusCrossbarFifoDepthPerClient).toString,
        "RTL_CONFIG_NUM_BUS_DEVICES"                    -> p(BusAddressMap).size.toString,
      )

      emitMacroGuards(w, macros)
    }

    private def renderFu(fu: FunctionalUnitDescriptor): String =
      braced(
        Seq(
          strLit(fu.name),
          enumLit("FunctionalUnitType", fu.`type`.cppName),
        )
      )

    private def renderDevice(dev: DeviceDescriptor): String =
      braced(
        Seq(
          strLit(dev.name),
          enumLit("DeviceType", dev.`type`.cppName),
          hex64(dev.base),
          hex64(dev.size),
        )
      )
  }

  sealed private trait CppDecl {
    def emit(w: Writer): Unit
  }

  final private case class EnumDecl(
    name: String,
    underlying: String,
    values: Seq[(String, Int)]
  ) extends CppDecl {
    override def emit(w: Writer): Unit = {
      w.line(s"enum class $name : $underlying {")
      w.indent {
        values.foreach { case (n, v) =>
          w.line(s"$n = $v,")
        }
      }
      w.line("};")
    }
  }

  final private case class StructDecl(
    name: String,
    fields: Seq[(String, String)]
  ) extends CppDecl {
    override def emit(w: Writer): Unit = {
      w.line(s"struct $name {")
      w.indent {
        fields.foreach { case (tpe, name) =>
          w.line(s"$tpe $name;")
        }
      }
      w.line("};")
    }
  }

  sealed private trait CppValue {
    def emit(w: Writer, p: Parameters): Unit
  }

  final private case class ScalarValue(
    tpe: String,
    name: String,
    value: Parameters => String
  ) extends CppValue {
    override def emit(w: Writer, p: Parameters): Unit =
      w.line(s"inline constexpr $tpe $name = ${value(p)};")
  }

  final private case class AliasValue(
    tpe: String,
    name: String,
    expr: String
  ) extends CppValue {
    override def emit(w: Writer, p: Parameters): Unit =
      w.line(s"inline constexpr $tpe $name = $expr;")
  }

  final private case class StructValue(
    tpe: String,
    name: String,
    fields: Parameters => Seq[String]
  ) extends CppValue {
    override def emit(w: Writer, p: Parameters): Unit =
      w.line(s"inline constexpr $tpe $name = ${braced(fields(p))};")
  }

  final private case class ArrayValue(
    name: String,
    elemType: String,
    sizeName: String,
    values: Parameters => Seq[String]
  ) extends CppValue {
    override def emit(w: Writer, p: Parameters): Unit = {
      w.line(s"inline constexpr std::array<$elemType, $sizeName> $name = {{")
      w.indent {
        values(p).foreach(v => w.line(s"$v,"))
      }
      w.line("}};")
    }
  }

  private def str(name: String, value: Parameters => String): CppValue =
    ScalarValue("std::string_view", name, p => strLit(value(p)))

  private def bool(name: String, field: Field[Boolean]): CppValue =
    ScalarValue("bool", name, p => boolLit(p(field)))

  private def bool(name: String, value: Parameters => Boolean): CppValue =
    ScalarValue("bool", name, p => boolLit(value(p)))

  private def u32(name: String, field: Field[Int]): CppValue =
    ScalarValue("uint32_t", name, p => u32Lit(p(field)))

  private def u32(name: String, value: Parameters => Int): CppValue =
    ScalarValue("uint32_t", name, p => u32Lit(value(p)))

  private def u64(name: String, field: Field[Long]): CppValue =
    ScalarValue("uint64_t", name, p => hex64(p(field)))

  private def cppEnum(
    tpe: String,
    name: String,
    value: Parameters => String
  ): CppValue =
    ScalarValue(tpe, name, p => enumLit(tpe, value(p)))

  private def alias(tpe: String, name: String, expr: String): CppValue =
    AliasValue(tpe, name, expr)

  private def struct(
    tpe: String,
    name: String,
    fields: Parameters => Seq[String]
  ): CppValue =
    StructValue(tpe, name, fields)

  private def array(
    name: String,
    elemType: String,
    sizeName: String,
    values: Parameters => Seq[String]
  ): CppValue =
    ArrayValue(name, elemType, sizeName, values)

  final private class Writer {
    private val sb    = new StringBuilder
    private var level = 0

    def line(s: String = ""): Unit = {
      if (s.nonEmpty) {
        sb.append("  " * level)
        sb.append(s)
      }
      sb.append('\n')
    }

    def indent(body: => Unit): Unit = {
      level += 1
      body
      level -= 1
    }

    def namespace(name: String)(body: => Unit): Unit = {
      line(s"namespace $name {")
      indent(body)
      line(s"} // namespace $name")
    }

    def result: String =
      sb.result()
  }

  private def emitMacroGuards(w: Writer, macros: Seq[(String, String)]): Unit = {
    val seen = scala.collection.mutable.LinkedHashSet.empty[String]

    macros.foreach { case (name, value) =>
      if (!seen.contains(name)) {
        seen += name

        w.line(s"#ifndef $name")
        w.line(s"#define $name $value")
        w.line("#endif")
        w.line()
      }
    }
  }

  private def braced(fields: Seq[String]): String =
    fields.mkString("{", ", ", "}")

  private def replCpp(p: ReplPolicy): String =
    p match {
      case ReplPolicy.Random    => "RANDOM"
      case ReplPolicy.FIFO      => "FIFO"
      case ReplPolicy.LFU       => "LFU"
      case ReplPolicy.LRU       => "LRU"
      case ReplPolicy.PseudoLRU => "PSEUDO_LRU"
      case _                    => "UNKNOWN"
    }

  private def busCpp(s: String): String =
    s match {
      case "axil" => "AXIL"
      case "axif" => "AXIF"
      case _      => "UNKNOWN"
    }

  private def boolLit(x: Boolean): String =
    if (x) "true" else "false"

  private def u32Lit(x: Int): String =
    s"${x}u"

  private def strLit(s: String): String =
    "\"" + escape(s) + "\""

  private def enumLit(tpe: String, value: String): String =
    s"$tpe::$value"

  private def hex32(x: BigInt): String = {
    val masked = x & BigInt("ffffffff", 16)
    "0x%08xu".format(masked.toLong)
  }

  private def hex64(x: Long): String =
    "0x%016xull".format(x)

  private def escape(s: String): String =
    s.flatMap {
      case '\\' => "\\\\"
      case '"'  => "\\\""
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case c    => c.toString
    }

  private def macroIdent(s: String): String =
    s.map {
      case c if c.isLetterOrDigit => c.toUpper
      case _                      => '_'
    }.mkString
}
