package arch.cpp.gen

import arch.system.device.DeviceDescriptor
import arch.configs._
import arch.cpp.CppCodegenOptions
import arch.cpp.dsl.{ CppWriter, CppDecl, EnumDecl, StructDecl, CppValue }
import arch.cpp.dsl.CppEnumMapping._
import arch.cpp.dsl.CppLiteral._
import arch.cpp.dsl.CppValueDsl._

private[cpp] object CppSysSchema {
  private val typeDecls: Seq[CppDecl] = Seq(
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
      u32("BYTES_PER_WORD", BytesPerWord),
      u32("BYTES_PER_INSTR", BytesPerInstr),
      u32("PC_STEP", PCStep),
      u64("RESET_VECTOR", ResetVector),
      u32("ISSUE_WIDTH", IssueWidth),
      u32("NUM_FUS", NumFUs),
      cppEnum("ReplPolicy", "BTB_REPL_POLICY", p => repl(p(BTBReplPolicy))),
      cppEnum("BusType", "BUS_TYPE", p => bus(p(BusType))),
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
