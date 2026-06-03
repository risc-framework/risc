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
import arch.core.interrupt.InterruptInit
import arch.core.exception.ExceptionInit
import arch.system.soc.Soc
import arch.system.bridge.BusBridgeInit
import arch.system.crossbar.BusCrossbarInit
import arch.configs._
import cpp.CppCodegen
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
  InterruptInit
  ExceptionInit

  BusBridgeInit
  BusCrossbarInit

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
