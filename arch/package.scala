package arch

package object configs {
  import core.fupool.{ FunctionalUnitDescriptor, FunctionalUnitType }
  import system.device.DeviceDescriptor
  import isa._
  import vcache.{ CacheParams, CacheAccess, CacheMissMode }
  import vcache.repl.ReplPolicy
  import chisel3.util.{ BitPat, log2Ceil }

  // NOTE: User Options: every one of these must be manually set

  // --------------------------------------------
  // Architecture Parameters
  object ISA       extends Field[Isa]
  object Frequency extends Field[Long]

  // Ifu Parameters
  object IBufferSize extends Field[Int]
  object ResetVector extends Field[Long]

  // Regfile Parameters
  object IsRegfileUseBypass extends Field[Boolean]
  object NumPhyRegs         extends Field[Int]

  // Scheduler Parameters
  object ScheduleType    extends Field[String]
  object IssueWidth      extends Field[Int]
  object CommitWidth     extends Field[Int]
  object FunctionalUnits extends Field[Seq[FunctionalUnitDescriptor]]

  // Decoder Parameters
  object DecodeKind extends Field[String]

  // Mult Parameters
  object MultPipelineStages extends Field[Int]

  // ROB Parameters
  object RobSize extends Field[Int]

  // Mem Parameters
  object StoreBufferSize extends Field[Int]

  // Branch Prediction
  object BpuPredictorKind extends Field[String]
  object BTBWays          extends Field[Int]
  object BTBSets          extends Field[Int]
  object BTBReplPolicy    extends Field[ReplPolicy]
  object GShareGhrWidth   extends Field[Int]

  // Cache Parameters
  object L1ICacheWays       extends Field[Int]
  object L1ICacheSets       extends Field[Int]
  object L1ICacheLineSize   extends Field[Int]
  object L1ICacheReplPolicy extends Field[ReplPolicy]

  object L1DCacheWays       extends Field[Int]
  object L1DCacheSets       extends Field[Int]
  object L1DCacheLineSize   extends Field[Int]
  object L1DCacheReplPolicy extends Field[ReplPolicy]

  // Bus Parameters
  object BusType                       extends Field[String]
  object BusCrossbarFifoDepthPerClient extends Field[Int]
  object BusRouteQueuePipe             extends Field[Boolean]
  object BusAddressMap                 extends Field[Seq[DeviceDescriptor]]
  // --------------------------------------------

  // NOTE: Derived parameters. These are computed from the manually-set user parameters.

  object XLen             extends Field[Int](p => p(ISA).xlen)
  object ILen             extends Field[Int](p => p(ISA).ilen)
  object NumArchRegs      extends Field[Int](p => p(ISA).numArchRegs)
  object IsBigEndian      extends Field[Boolean](p => p(ISA).isBigEndian)
  object Bubble           extends Field[BitPat](p => p(ISA).bubble)
  object BytesPerWord     extends Field[Int](p => p(XLen) / 8)
  object BytesOffsetWidth extends Field[Int](p => log2Ceil(p(BytesPerWord)))
  object BytesPerInstr    extends Field[Int](p => p(ILen) / 8)
  object PCStep           extends Field[Int](p => p(BytesPerInstr))
  object PCAlign          extends Field[Int](p => log2Ceil(p(BytesPerInstr)))

  object FuTypeWidth extends Field[Int](_ => log2Ceil(FunctionalUnitType.values.size))
  object FuIdWidth   extends Field[Int](p => log2Ceil(p(FunctionalUnits).size))
  object NumFUs      extends Field[Int](p => p(FunctionalUnits).size)

  object NumLDs
      extends Field[Int](p =>
        p(FunctionalUnits).count(_.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD)
      )

  object NumSTs
      extends Field[Int](p =>
        p(FunctionalUnits).count(_.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST)
      )

  object NumBRUs
      extends Field[Int](p =>
        p(FunctionalUnits).count(_.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_BRU)
      )

  object NumCSRs
      extends Field[Int](p =>
        p(FunctionalUnits).count(_.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR)
      )

  object RobTagWidth extends Field[Int](p => log2Ceil(p(RobSize)))

  object L1ICacheParams
      extends Field[CacheParams](p =>
        CacheParams(
          addrWidth = p(XLen),
          dataWidth = p(IssueWidth) * p(ILen),
          wordsPerLine = p(L1ICacheLineSize) / (p(IssueWidth) * p(BytesPerInstr)),
          numSets = p(L1ICacheSets),
          numWays = p(L1ICacheWays),
          access = CacheAccess.ReadOnly,
          missMode = CacheMissMode.NonBlocking,
          replPolicy = p(L1ICacheReplPolicy),
          sourceWidth = 1
        )
      )

  object L1DCacheParams
      extends Field[CacheParams](p =>
        CacheParams(
          addrWidth = p(XLen),
          dataWidth = p(XLen),
          wordsPerLine = p(L1DCacheLineSize) / p(BytesPerWord),
          numSets = p(L1DCacheSets),
          numWays = p(L1DCacheWays),
          access = CacheAccess.ReadWrite,
          missMode = CacheMissMode.NonBlocking,
          replPolicy = p(L1DCacheReplPolicy),
          sourceWidth = 1
        )
      )

  object ConfigFields {
    val user: Seq[Field[_]] = Seq(
      ISA,
      Frequency,
      IBufferSize,
      ResetVector,
      IsRegfileUseBypass,
      NumPhyRegs,
      ScheduleType,
      IssueWidth,
      FunctionalUnits,
      DecodeKind,
      MultPipelineStages,
      RobSize,
      StoreBufferSize,
      BpuPredictorKind,
      BTBWays,
      BTBSets,
      BTBReplPolicy,
      GShareGhrWidth,
      L1ICacheWays,
      L1ICacheSets,
      L1ICacheLineSize,
      L1ICacheReplPolicy,
      L1DCacheWays,
      L1DCacheSets,
      L1DCacheLineSize,
      L1DCacheReplPolicy,
      BusType,
      BusCrossbarFifoDepthPerClient,
      BusRouteQueuePipe,
      BusAddressMap
    )

    val derived: Seq[Field[_]] = Seq(
      XLen,
      ILen,
      NumArchRegs,
      IsBigEndian,
      Bubble,
      BytesPerWord,
      BytesOffsetWidth,
      BytesPerInstr,
      PCStep,
      PCAlign,
      FuTypeWidth,
      FuIdWidth,
      NumFUs,
      NumLDs,
      NumSTs,
      NumBRUs,
      NumCSRs,
      RobTagWidth,
      L1ICacheParams,
      L1DCacheParams
    )

    val all: Seq[Field[_]] = user ++ derived
  }
}
