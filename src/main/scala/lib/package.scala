package arch

package object configs {
  import core.fupool.{ FunctionalUnitDescriptor, FunctionalUnitType }
  import isa._
  import system.device.DeviceDescriptor
  import vcache.{ CacheAccess, CacheMissMode, CacheParams }
  import vcache.repl.ReplPolicy
  import chisel3.util.{ BitPat, log2Ceil }

  private[configs] def requirePositive(name: String, value: Int): Int = {
    require(value > 0, s"$name must be positive, got $value")
    value
  }

  private[configs] def requireNonNegative(name: String, value: Int): Int = {
    require(value >= 0, s"$name must be non-negative, got $value")
    value
  }

  private[configs] def bytesFromBits(name: String, bits: Int): Int = {
    require(bits > 0, s"$name must be positive, got $bits")
    require(bits % 8 == 0, s"$name must be byte-aligned, got $bits bits")
    bits / 8
  }

  private[configs] def checkedDiv(name: String, lhs: Int, rhs: Int): Int = {
    require(lhs > 0, s"$name numerator must be positive, got $lhs")
    require(rhs > 0, s"$name denominator must be positive, got $rhs")
    require(lhs % rhs == 0, s"$name requires exact division, got $lhs / $rhs")
    lhs / rhs
  }

  private[configs] def widthForCount(name: String, count: Int): Int = {
    require(count > 0, s"$name count must be positive, got $count")
    math.max(1, log2Ceil(count))
  }

  // --------------------------------------------
  // User parameters. These should be manually supplied by the selected Config.

  // Architecture Parameters
  object ISA           extends Field[Isa]
  object TopModuleName extends Field[String]
  object Frequency     extends Field[Long]

  // IFU Parameters
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
  object RobStorageType extends Field[String]
  object RobSize        extends Field[Int]

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
  // Derived ISA parameters.

  object XLen
      extends Field[Int](p => {
        val xlen = p(ISA).xlen
        requirePositive("XLen", xlen)
      })

  object ILen
      extends Field[Int](p => {
        val ilen = p(ISA).ilen
        requirePositive("ILen", ilen)
      })

  object NumArchRegs
      extends Field[Int](p => {
        val n = p(ISA).numArchRegs
        requirePositive("NumArchRegs", n)
      })

  object IsBigEndian extends Field[Boolean](p => p(ISA).isBigEndian)

  object Bubble extends Field[BitPat](p => p(ISA).bubble)

  object BytesPerWord extends Field[Int](p => bytesFromBits("XLen", p(XLen)))

  object BytesOffsetWidth extends Field[Int](p => widthForCount("BytesPerWord", p(BytesPerWord)))

  object BytesPerInstr extends Field[Int](p => bytesFromBits("ILen", p(ILen)))

  object PCStep extends Field[Int](p => p(BytesPerInstr))

  object PCAlign extends Field[Int](p => widthForCount("BytesPerInstr", p(BytesPerInstr)))

  // --------------------------------------------
  // Derived FU parameters.

  object FuTypeWidth
      extends Field[Int](_ => widthForCount("FunctionalUnitType", FunctionalUnitType.values.size))

  object NumFUs
      extends Field[Int](p => {
        val n = p(FunctionalUnits).size
        requirePositive("NumFUs", n)
      })

  object FuIdWidth extends Field[Int](p => widthForCount("NumFUs", p(NumFUs)))

  object NumLDs
      extends Field[Int](p =>
        requireNonNegative(
          "NumLDs",
          p(FunctionalUnits).count(_.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD)
        )
      )

  object NumSTs
      extends Field[Int](p =>
        requireNonNegative(
          "NumSTs",
          p(FunctionalUnits).count(_.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST)
        )
      )

  object NumBRUs
      extends Field[Int](p =>
        requireNonNegative(
          "NumBRUs",
          p(FunctionalUnits).count(_.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_BRU)
        )
      )

  object NumCSRs
      extends Field[Int](p =>
        requireNonNegative(
          "NumCSRs",
          p(FunctionalUnits).count(_.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR)
        )
      )

  object RobTagWidth extends Field[Int](p => widthForCount("RobSize", p(RobSize)))

  // --------------------------------------------
  // Derived cache parameters.

  object L1ICacheParams
      extends Field[CacheParams](p => {
        val fetchBits    = p(IssueWidth) * p(ILen)
        val fetchBytes   = p(IssueWidth) * p(BytesPerInstr)
        val wordsPerLine = checkedDiv("L1ICache wordsPerLine", p(L1ICacheLineSize), fetchBytes)

        CacheParams(
          addrWidth = p(XLen),
          dataWidth = fetchBits,
          wordsPerLine = wordsPerLine,
          numSets = p(L1ICacheSets),
          numWays = p(L1ICacheWays),
          access = CacheAccess.ReadOnly,
          missMode = CacheMissMode.NonBlocking,
          replPolicy = p(L1ICacheReplPolicy)
        )
      })

  object L1DCacheParams
      extends Field[CacheParams](p => {
        val wordsPerLine = checkedDiv("L1DCache wordsPerLine", p(L1DCacheLineSize), p(BytesPerWord))

        CacheParams(
          addrWidth = p(XLen),
          dataWidth = p(XLen),
          wordsPerLine = wordsPerLine,
          numSets = p(L1DCacheSets),
          numWays = p(L1DCacheWays),
          access = CacheAccess.ReadWrite,
          missMode = CacheMissMode.NonBlocking,
          replPolicy = p(L1DCacheReplPolicy)
        )
      })

  object ConfigFields {
    val user: Seq[Field[_]] = Seq(
      ISA,
      Frequency,
      TopModuleName,
      IBufferSize,
      ResetVector,
      IsRegfileUseBypass,
      NumPhyRegs,
      ScheduleType,
      IssueWidth,
      CommitWidth,
      FunctionalUnits,
      DecodeKind,
      MultPipelineStages,
      RobStorageType,
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

    val all: Seq[Field[_]] =
      user ++ derived
  }
}
