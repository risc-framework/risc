package arch

import arch.core.alu.AluInit
import arch.core.bpu.BpuInit
import arch.core.bru.BruInit
import arch.core.decode.DecodeInit
import arch.core.div.DivInit
import arch.core.pma.PmaInit
import arch.core.ld.LdInit
import arch.core.mult.MultInit
import arch.core.regfile.RegfileInit
import arch.core.scheduler.SchedulerInit
import arch.core.st.StInit
import arch.core.csr.CsrInit
import arch.core.exception.ExceptionInit
import arch.core.rob.RobInit
import arch.system.soc.Soc
import arch.system.bridge.BusBridgeInit
import arch.system.crossbar.BusCrossbarInit
import arch.system.device.{ DeviceDescriptor, DeviceType }
import arch.core.fupool.{ FunctionalUnitDescriptor, FunctionalUnitType }
import arch.configs._
import arch.isa.variants.riscv32._
import cpp.CppCodegen
import vcache.repl.ReplPolicy
import vutils._

object MainSystem extends App {
  DecodeInit
  BruInit
  BpuInit
  RegfileInit
  AluInit
  MultInit
  DivInit
  CsrInit
  PmaInit
  LdInit
  StInit
  SchedulerInit
  ExceptionInit
  RobInit

  BusBridgeInit
  BusCrossbarInit

  private val params = Parameters(
    Map(
      ISA                           -> Rv32im.isa,
      Frequency                     -> 50_000_000L,
      IBufferSize                   -> 16,
      ResetVector                   -> 0x80000000L,
      IsRegfileUseBypass            -> true,
      NumPhyRegs                    -> 64,
      ScheduleType                  -> "scoreboard",
      IssueWidth                    -> 2,
      CommitWidth                   -> 2,
      FunctionalUnits               -> Seq(
        FunctionalUnitDescriptor(
          name = "ALU_0",
          `type` = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ALU
        ),
        FunctionalUnitDescriptor(
          name = "ALU_1",
          `type` = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ALU
        ),
        FunctionalUnitDescriptor(
          name = "MULT_0",
          `type` = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_MULT
        ),
        FunctionalUnitDescriptor(
          name = "DIV_0",
          `type` = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_DIV
        ),
        FunctionalUnitDescriptor(
          name = "LD_0",
          `type` = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD
        ),
        FunctionalUnitDescriptor(
          name = "LD_1",
          `type` = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD
        ),
        FunctionalUnitDescriptor(
          name = "ST_0",
          `type` = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST
        ),
        FunctionalUnitDescriptor(
          name = "ST_1",
          `type` = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST
        ),
        FunctionalUnitDescriptor(
          name = "BRU_0",
          `type` = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_BRU
        ),
        FunctionalUnitDescriptor(
          name = "CSR",
          `type` = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR
        )
      ),
      DecodeKind                    -> "table",
      MultPipelineStages            -> 2,
      RobStorageType                -> "reg",
      RobSize                       -> 16,
      StoreBufferSize               -> 8,
      BpuPredictorKind              -> "gshare",
      BTBWays                       -> 2,
      BTBSets                       -> 128,
      BTBReplPolicy                 -> ReplPolicy.PseudoLRU,
      GShareGhrWidth                -> 10,
      L1ICacheWays                  -> 2,
      L1ICacheSets                  -> 8,
      L1ICacheLineSize              -> 64,
      L1ICacheReplPolicy            -> ReplPolicy.LRU,
      L1DCacheWays                  -> 4,
      L1DCacheSets                  -> 8,
      L1DCacheLineSize              -> 64,
      L1DCacheReplPolicy            -> ReplPolicy.PseudoLRU,
      BusType                       -> "axif",
      BusCrossbarFifoDepthPerClient -> 4,
      BusRouteQueuePipe             -> true,
      BusAddressMap                 -> Seq(
        DeviceDescriptor(
          name = "imem",
          `type` = DeviceType.DEVICE_TYPE_SRAM,
          base = 0x80000000L,
          size = 0x40000L
        ),
        DeviceDescriptor(
          name = "dmem",
          `type` = DeviceType.DEVICE_TYPE_SRAM,
          base = 0x80040000L,
          size = 0x40000L
        ),
        DeviceDescriptor(
          name = "uart",
          `type` = DeviceType.DEVICE_TYPE_UART,
          base = 0x10000000L,
          size = 0x1000L
        ),
        DeviceDescriptor(
          name = "clint",
          `type` = DeviceType.DEVICE_TYPE_IRH,
          base = 0x20000000L,
          size = 0x10000L
        )
      )
    )
  )

  params.requireExplicit(ConfigFields.user)

  implicit val p: Parameters = params.materialize(ConfigFields.derived)

  DesignEmitter.emit(
    gen = new Soc,
    filename = "soc",
    target = SystemVerilog,
    info = true,
    lowering = true,
  )

  CppCodegen.emit(
    p,
    "build/include/demu/generated/sys_def.hh",
    "build/include/demu/generated/isa_def.hh",
    "build/include/demu/generated/bus_bindings.hh",
    "build/include/demu/generated/retire_bindings.hh"
  )
}
