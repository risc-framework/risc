package arch.cpp

import arch.configs._
import CppEnumMapping._
import CppLiteral._
import CppValueDsl._

private[cpp] object CppSysSchema {
  private val typeDecls: Seq[CppDecl] = Seq(
    EnumDecl(
      "FunctionalUnitType",
      "uint8_t",
      Seq(
        "FUNCTIONAL_UNIT_TYPE_UNKNOWN" -> 0,
        "FUNCTIONAL_UNIT_TYPE_ALU"     -> 1,
        "FUNCTIONAL_UNIT_TYPE_MULT"    -> 2,
        "FUNCTIONAL_UNIT_TYPE_DIV"     -> 3,
        "FUNCTIONAL_UNIT_TYPE_LD"      -> 4,
        "FUNCTIONAL_UNIT_TYPE_ST"      -> 5,
        "FUNCTIONAL_UNIT_TYPE_BRU"     -> 6,
        "FUNCTIONAL_UNIT_TYPE_CSR"     -> 7,
      )
    ),
    EnumDecl(
      "DeviceType",
      "uint8_t",
      Seq(
        "DEVICE_TYPE_UNKNOWN" -> 0,
        "DEVICE_TYPE_SRAM"    -> 1,
        "DEVICE_TYPE_UART"    -> 2,
        "DEVICE_TYPE_IRH"     -> 3,
      )
    ),
    EnumDecl(
      "ReplPolicy",
      "uint8_t",
      Seq(
        "REPL_POLICY_UNKNOWN"    -> 0,
        "REPL_POLICY_RANDOM"     -> 1,
        "REPL_POLICY_FIFO"       -> 2,
        "REPL_POLICY_LFU"        -> 3,
        "REPL_POLICY_LRU"        -> 4,
        "REPL_POLICY_PSEUDO_LRU" -> 5,
      )
    ),
    EnumDecl(
      "BusType",
      "uint8_t",
      Seq(
        "BUS_TYPE_UNKNOWN" -> 0,
        "BUS_TYPE_AXIL"    -> 1,
        "BUS_TYPE_AXIF"    -> 2,
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

  private def scalarFields(options: CppCodegenOptions): Seq[CppValue] =
    Seq(
      u64("FREQ", Frequency),
      bool("ENABLE_DEBUG", EnableDebug),
      alias("std::string_view", "ISA_NAME", s"::${options.isaNamespace}::ISA_NAME"),
      alias("uint32_t", "XLEN", s"::${options.isaNamespace}::XLEN"),
      alias("uint32_t", "ILEN", s"::${options.isaNamespace}::ILEN"),
      alias("uint32_t", "NUM_ARCH_REGS", s"::${options.isaNamespace}::NUM_ARCH_REGS"),
      alias("uint32_t", "MICRO_OP_WIDTH", s"::${options.isaNamespace}::MICRO_OP_WIDTH"),
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
      cppEnum("ReplPolicy", "BTB_REPL_POLICY", p => repl(p(BTBReplPolicy))),
      u32("GSHARE_GHR_WIDTH", GShareGhrWidth),
      cppEnum("BusType", "BUS_TYPE", p => bus(p(BusType))),
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
          enumLit("ReplPolicy", repl(p(L1ICacheReplPolicy))),
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
          enumLit("ReplPolicy", repl(p(L1DCacheReplPolicy))),
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
    scalarFields(options).foreach(_.emit(w, p))
    w.line()

    aggregateFields.foreach { value =>
      value.emit(w, p)
      w.line()
    }
  }

  def emitMacros(w: CppWriter, p: Parameters): Unit = {
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
        cstrLit(fu.name),
        enumLit("FunctionalUnitType", fuType(fu.`type`)),
      )
    )

  private def renderDevice(dev: DeviceDescriptor): String =
    braced(
      Seq(
        cstrLit(dev.name),
        enumLit("DeviceType", deviceType(dev.`type`)),
        hex64(dev.base),
        hex64(dev.size),
      )
    )
}
