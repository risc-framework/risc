package arch

import system._
import system.bridge._
import system.crossbar._
import core._
import core.decoder._
import core.bru._
import core.regfile._
import core.alu._
import core.mult._
import core.div._
import core.lsu._
import core.imm._
import core.csr._
import configs._
import vutils._

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

  DesignEmitter.emit(
    gen = new RiscCore,
    filename = s"${p(ISA).name}_cpu",
    target = SystemVerilog,
    info = true,
    lowering = true,
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

  BusBridgeInit
  BusCrossbarInit

  DesignEmitter.emit(
    gen = new RiscSystem,
    filename = s"${p(ISA).name}_system",
    target = SystemVerilog,
    info = true,
    lowering = true,
  )
}
