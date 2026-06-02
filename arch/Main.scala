package arch

import arch.node.alu.AluInit
import arch.node.bpu.BpuInit
import arch.node.bru.BruInit
import arch.node.decoder.DecoderInit
import arch.node.div.DivInit
import arch.node.imm.ImmInit
import arch.node.pma.PmaInit
import arch.node.ld.LdInit
import arch.node.mult.MultInit
import arch.node.regfile.RegfileInit
import arch.node.scheduler.SchedulerInit
import arch.node.st.StInit
import arch.node.csr.CsrInit
import arch.node.interrupt.InterruptInit
import arch.node.exception.ExceptionInit
import arch.system.RiscSystem
import arch.system.bridge.BusBridgeInit
import arch.system.crossbar.BusCrossbarInit
import arch.configs._
import cpp.CppCodegen
import vutils._

object MainSystem extends App {
  ImmInit
  DecoderInit
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
    gen = new RiscSystem,
    filename = s"${p(ISA).name}_system",
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
