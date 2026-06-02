package arch.system.soc

import arch.configs._
import arch.core.cpu.{ Cpu, DebugIO }
import arch.core.csr.InterruptLines
import arch.system.bridge.BusBridge
import arch.system.crossbar.{ BusCrossbar, BusCrossbarUtilsFactory }
import chisel3._

class Soc(implicit p: Parameters) extends Module {
  override def desiredName: String = "soc"

  private val crossbarUtils = BusCrossbarUtilsFactory.getOrThrow(p(BusType))

  val devices = IO(Vec(p(BusAddressMap).length, crossbarUtils.slaveType))
    .suggestName(s"M_${p(BusType)}".toUpperCase)
  val irq     = IO(Input(new InterruptLines))
  val debug   = IO(Output(new DebugIO))

  private val cpu      = Module(new Cpu)
  private val bridge   = Module(new BusBridge)
  private val crossbar = Module(new BusCrossbar)

  dontTouch(devices)

  cpu.io.imem <> bridge.imem
  cpu.io.dmem <> bridge.dmem
  cpu.io.mmio <> bridge.mmio
  cpu.io.irq := irq

  crossbarUtils.connect(crossbar.ibus, bridge.ibus)
  crossbarUtils.connect(crossbar.dbus, bridge.dbus)
  crossbarUtils.connect(crossbar.mbus, bridge.mbus)

  for (i <- 0 until p(BusAddressMap).length)
    devices(i) <> crossbar.devices(i)

  debug <> cpu.io.debug
}
