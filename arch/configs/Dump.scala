package arch.configs

import proto._
import vcache.repl._
import scalapb.json4s.JsonFormat
import java.nio.file.{ Files, Paths }
import java.nio.charset.StandardCharsets

object RiscDump {
  def buildConfig(p: Parameters): RiscConfig =
    RiscConfig(
      freq = p(Frequency),
      enableDebug = p(EnableDebug),
      ifu = Some(
        IfuConfig(
          ibufferSize = p(IBufferSize),
          issueWidth = p(IssueWidth),
          resetVector = p(ResetVector),
        )
      ),
      bpu = Some(
        BpuConfig(
          btb = Some(
            BtbConfig(
              sets = p(BTBSets),
              ways = p(BTBWays),
              replPolicy = toProtoRepl(p(BTBReplPolicy)),
            )
          ),
          gshareGhrWidth = p(GShareGhrWidth),
        )
      ),
      regfile = Some(
        RegfileConfig(
          numPhyRegs = p(NumPhyRegs),
          useBypass = p(IsRegfileUseBypass),
        )
      ),
      scheduler = Some(
        SchedulerConfig(policy = toProtoSchduler(p(ScheduleType)), fus = p(FunctionalUnits))
      ),
      rob = Some(
        RobConfig(
          size = p(RobSize),
        )
      ),
      mem = Some(
        MemConfig(
          storeBufferSize = p(StoreBufferSize),
        )
      ),
      l1I = Some(
        CacheConfig(
          sets = p(L1ICacheSets),
          ways = p(L1ICacheWays),
          lineSize = p(L1ICacheLineSize),
          replPolicy = toProtoRepl(p(L1ICacheReplPolicy)),
        )
      ),
      l1D = Some(
        CacheConfig(
          sets = p(L1DCacheSets),
          ways = p(L1DCacheWays),
          lineSize = p(L1DCacheLineSize),
          replPolicy = toProtoRepl(p(L1DCacheReplPolicy)),
        )
      ),
      bus = Some(
        BusConfig(
          `type` = toProtoBus(p(arch.configs.BusType)),
          crossbarFifoDepth = p(BusCrossbarFifoDepthPerClient),
          addressMap = p(BusAddressMap),
        )
      ),
    )

  def dump(
    p: Parameters,
    configPath: String,
    isaPath: String,
    binPath: Option[String] = None,
    isaBinPath: Option[String] = None,
  ): Unit = {
    dumpConfig(p, configPath, binPath)
    dumpIsa(p, isaPath, isaBinPath)
  }

  def dumpConfig(p: Parameters, jsonPath: String, binPath: Option[String] = None): Unit = {
    val cfg = buildConfig(p)
    writeJson(cfg, jsonPath)
    binPath.foreach(bp => writeBin(cfg.toByteArray, bp))
    println(s"[RiscDump] config → $jsonPath")
  }

  def dumpIsa(p: Parameters, jsonPath: String, binPath: Option[String] = None): Unit = {
    val isa = p(ISA).isa
    writeJson(isa, jsonPath)
    binPath.foreach(bp => writeBin(isa.toByteArray, bp))
    println(s"[RiscDump] isa → $jsonPath")
  }

  private def writeJson(msg: scalapb.GeneratedMessage, path: String): Unit = {
    val json = JsonFormat.toJsonString(msg)
    Files.createDirectories(Paths.get(path).getParent)
    Files.write(Paths.get(path), json.getBytes(StandardCharsets.UTF_8))
  }

  private def writeBin(bytes: Array[Byte], path: String): Unit = {
    Files.createDirectories(Paths.get(path).getParent)
    Files.write(Paths.get(path), bytes)
  }

  private def toProtoRepl(p: ReplacementPolicy): ReplPolicy = p match {
    case ReplacementPolicy.Random    => ReplPolicy.REPL_POLICY_RANDOM
    case ReplacementPolicy.FIFO      => ReplPolicy.REPL_POLICY_FIFO
    case ReplacementPolicy.LFU       => ReplPolicy.REPL_POLICY_LFU
    case ReplacementPolicy.LRU       => ReplPolicy.REPL_POLICY_LRU
    case ReplacementPolicy.PseudoLRU => ReplPolicy.REPL_POLICY_PSEUDO_LRU
    case _                           => ReplPolicy.REPL_POLICY_UNKNOWN
  }

  private def toProtoBus(s: String): BusType = s match {
    case "axil" => BusType.BUS_TYPE_AXIL
    case "axif" => BusType.BUS_TYPE_AXIF
    case _      => BusType.BUS_TYPE_UNKNOWN
  }

  private def toProtoSchduler(s: String): SchedulePolicy = s match {
    case "scoreboard" => SchedulePolicy.SCHEDULE_POLICY_SCOREBOARD
    case _            => SchedulePolicy.SCHEDULE_POLICY_UNSPECIFIED
  }
}
