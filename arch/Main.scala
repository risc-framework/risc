package arch

import arch.system.RiscSystem
import arch.system.bridge.BusBridgeInit
import arch.system.crossbar.BusCrossbarInit
import arch.core.RiscCore
import arch.core.decoder.DecoderInit
import arch.core.bru.BruInit
import arch.core.regfile.RegfileInit
import arch.core.alu.AluInit
import arch.core.mult.MultInit
import arch.core.div.DivInit
import arch.core.lsu.LoadStoreInit
import arch.core.imm.ImmInit
import arch.core.csr.CsrInit
import arch.core.fu.FUInit
import arch.configs._
import arch.cpp.CppCodegen
import vutils.{ DesignEmitter, SystemVerilog }

object MainCore extends App {
  DecoderInit
  BruInit
  RegfileInit
  AluInit
  MultInit
  DivInit
  LoadStoreInit
  ImmInit
  CsrInit
  FUInit

  DesignEmitter.emit(
    gen = new RiscCore,
    filename = s"${p(ISA).name}_cpu",
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

object MainSystem extends App {
  DecoderInit
  BruInit
  RegfileInit
  AluInit
  MultInit
  DivInit
  LoadStoreInit
  ImmInit
  CsrInit
  FUInit

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
