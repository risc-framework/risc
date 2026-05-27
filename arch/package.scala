package arch

package object configs {
  import core.fu.{ FunctionalUnitDescriptor, FunctionalUnitType }
  import system.{ DeviceDescriptor, DeviceType }
  import isa._
  import vcache._
  import vcache.repl._
  import chisel3.util.{ BitPat, log2Ceil }

  // NOTE: User Options: You should only modify these parameters

  // --------------------------------------------
  // Architecture Parameters
  object ISA         extends Field[IsaWrapper](RV32IM)
  object Frequency   extends Field[Long](50_000_000L) // NOTE: default: 50MHZ
  object EnableDebug extends Field[Boolean](true)

  // Ifu Parameters
  object IBufferSize extends Field[Int](16)
  object ResetVector extends Field[Long](0x80000000L)

  // Regfile Parameters
  object IsRegfileUseBypass extends Field[Boolean](true)
  object NumPhyRegs         extends Field[Int](64)

  // Scheduler Parameters
  object ScheduleType extends Field[String]("scoreboard")
  object IssueWidth   extends Field[Int](2)
  object FunctionalUnits
      extends Field[Seq[FunctionalUnitDescriptor]](
        Seq(
          FunctionalUnitDescriptor(name = "ALU_0", `type` = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ALU),
          FunctionalUnitDescriptor(name = "ALU_1", `type` = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ALU),
          FunctionalUnitDescriptor(name = "MULT_0", `type` = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_MULT),
          FunctionalUnitDescriptor(name = "DIV_0", `type` = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_DIV),
          FunctionalUnitDescriptor(name = "LD_0", `type` = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD),
          FunctionalUnitDescriptor(name = "LD_1", `type` = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD),
          FunctionalUnitDescriptor(name = "ST_0", `type` = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST),
          FunctionalUnitDescriptor(name = "ST_1", `type` = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST),
          FunctionalUnitDescriptor(name = "BRU_0", `type` = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_BRU),
          FunctionalUnitDescriptor(name = "CSR", `type` = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR),
        )
      )

  // Mult Parameters
  object MultPipelineStages extends Field[Int](2)

  // ROB Parameters
  object RobSize extends Field[Int](16)

  // Mem Parameters
  object StoreBufferSize extends Field[Int](8)

  // Branch Prediction
  object BTBWays        extends Field[Int](2)
  object BTBSets        extends Field[Int](128)
  object BTBReplPolicy  extends Field[ReplPolicy](ReplPolicy.PseudoLRU)
  object GShareGhrWidth extends Field[Int](10)

  // Cache Parameters
  object L1ICacheWays       extends Field[Int](2)
  object L1ICacheSets       extends Field[Int](8)
  object L1ICacheLineSize   extends Field[Int](64) // in bytes
  object L1ICacheReplPolicy extends Field[ReplPolicy](ReplPolicy.LRU)

  object L1DCacheWays       extends Field[Int](4)
  object L1DCacheSets       extends Field[Int](8)
  object L1DCacheLineSize   extends Field[Int](64) // in bytes
  object L1DCacheReplPolicy extends Field[ReplPolicy](ReplPolicy.PseudoLRU)

  // Bus Parameters
  object BusType                       extends Field[String]("axif")
  object BusCrossbarFifoDepthPerClient extends Field[Int](4)

  object BusAddressMap
      extends Field[Seq[DeviceDescriptor]](
        Seq(
          DeviceDescriptor(name = "imem", `type` = DeviceType.DEVICE_TYPE_SRAM, base = 0x80000000L, size = 0x40000L),
          DeviceDescriptor(name = "dmem", `type` = DeviceType.DEVICE_TYPE_SRAM, base = 0x80040000L, size = 0x40000L),
          DeviceDescriptor(name = "uart", `type` = DeviceType.DEVICE_TYPE_UART, base = 0x10000000L, size = 0x1000L),
          DeviceDescriptor(name = "clint", `type` = DeviceType.DEVICE_TYPE_IRH, base = 0x20000000L, size = 0x10000L)
        )
      )
  // --------------------------------------------

  // NOTE: You should not modify the parameters below, as they are derived from the user options above
  // Architecture Parameters
  object XLen             extends Field[Int](ISA().xlen)
  object ILen             extends Field[Int](ISA().ilen)
  object NumArchRegs      extends Field[Int](ISA().numArchRegs)
  object IsBigEndian      extends Field[Boolean](ISA().isBigEndian)
  object MicroOpWidth     extends Field[Int](ISA().microOpWidth)
  object Bubble           extends Field[BitPat](ISA().bubble)
  object BytesPerWord     extends Field[Int](ISA().xlen / 8)
  object BytesOffsetWidth extends Field[Int](log2Ceil(ISA().xlen / 8))
  object BytesPerInstr    extends Field[Int](ISA().ilen / 8)
  object PCStep           extends Field[Int](ISA().ilen / 8)
  object PCAlign          extends Field[Int](log2Ceil(ISA().ilen / 8))

  // Derived parameters
  object FuTypeWidth extends Field[Int](log2Ceil(FunctionalUnitType.values.size))
  object FuIdWidth   extends Field[Int](log2Ceil(FunctionalUnits().size))
  object NumFUs      extends Field[Int](FunctionalUnits().size)
  object NumLDs      extends Field[Int](FunctionalUnits().count(_.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD))
  object RobTagWidth extends Field[Int](log2Ceil(RobSize()))

  object L1ICacheParams
      extends Field[CacheParams](
        CacheParams(
          addrWidth = XLen(),
          dataWidth = IssueWidth() * ILen(),
          L1ICacheLineSize() / (IssueWidth() * BytesPerInstr()),
          numSets = L1ICacheSets(),
          numWays = L1ICacheWays(),
          access = CacheAccess.ReadOnly,
          missMode = CacheMissMode.NonBlocking,
          replPolicy = L1ICacheReplPolicy(),
          sourceWidth = 1
        )
      )

  object L1DCacheParams
      extends Field[CacheParams](
        CacheParams(
          addrWidth = XLen(),
          dataWidth = XLen(),
          wordsPerLine = L1DCacheLineSize() / BytesPerWord(),
          numSets = L1DCacheSets(),
          numWays = L1DCacheWays(),
          access = CacheAccess.ReadWrite,
          missMode = CacheMissMode.NonBlocking,
          replPolicy = L1DCacheReplPolicy(),
          sourceWidth = 1
        )
      )

  implicit val p: Parameters = Parameters(
    Map(
      ISA         -> ISA(),
      Frequency   -> Frequency(),
      EnableDebug -> EnableDebug(),

      // ISA
      XLen             -> XLen(),
      ILen             -> ILen(),
      NumArchRegs      -> NumArchRegs(),
      IsBigEndian      -> IsBigEndian(),
      MicroOpWidth     -> MicroOpWidth(),
      Bubble           -> Bubble(),
      BytesPerWord     -> BytesPerWord(),
      BytesOffsetWidth -> BytesOffsetWidth(),
      BytesPerInstr    -> BytesPerInstr(),
      PCStep           -> PCStep(),
      PCAlign          -> PCAlign(),

      // IFU
      IBufferSize -> IBufferSize(),
      ResetVector -> ResetVector(),

      // Regfile
      IsRegfileUseBypass -> IsRegfileUseBypass(),
      NumPhyRegs         -> NumPhyRegs(),

      // Scheduler
      ScheduleType    -> ScheduleType(),
      IssueWidth      -> IssueWidth(),
      FunctionalUnits -> FunctionalUnits(),
      FuTypeWidth     -> FuTypeWidth(),
      FuIdWidth       -> FuIdWidth(),
      NumLDs          -> NumLDs(),

      // Mult
      MultPipelineStages -> MultPipelineStages(),

      // ROB
      RobSize     -> RobSize(),
      RobTagWidth -> RobTagWidth(),

      // Branch Prediction
      BTBWays        -> BTBWays(),
      BTBSets        -> BTBSets(),
      BTBReplPolicy  -> BTBReplPolicy(),
      GShareGhrWidth -> GShareGhrWidth(),

      // Cache
      L1ICacheWays       -> L1ICacheWays(),
      L1ICacheSets       -> L1ICacheSets(),
      L1ICacheLineSize   -> L1ICacheLineSize(),
      L1ICacheReplPolicy -> L1ICacheReplPolicy(),
      L1ICacheParams     -> L1ICacheParams(),
      L1DCacheWays       -> L1DCacheWays(),
      L1DCacheSets       -> L1DCacheSets(),
      L1DCacheLineSize   -> L1DCacheLineSize(),
      L1DCacheReplPolicy -> L1DCacheReplPolicy(),
      L1DCacheParams     -> L1DCacheParams(),

      // Bus
      BusType                       -> BusType(),
      BusCrossbarFifoDepthPerClient -> BusCrossbarFifoDepthPerClient(),
      BusAddressMap                 -> BusAddressMap(),
    )
  )
}
