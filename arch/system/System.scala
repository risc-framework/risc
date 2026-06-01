package arch.system

import arch.system.bridge.{ BusBridge, BusBridgeUtilsFactory }
import arch.system.crossbar.{ BusCrossbar, BusCrossbarUtilsFactory }
import arch.core.{ DebugIO, RiscCore }
import arch.node.Core
import arch.core.csr.CoreInterruptIO
import arch.configs._
import chisel3._

class RiscSystem(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_system"

  val bridge_utils   = BusBridgeUtilsFactory.getOrThrow(p(BusType))
  val crossbar_utils = BusCrossbarUtilsFactory.getOrThrow(p(BusType))

  val devices = IO(Vec(p(BusAddressMap).length, crossbar_utils.slaveType))
    .suggestName(s"M_${p(BusType)}".toUpperCase)
  val irq     = IO(new CoreInterruptIO)

  val debug = IO(new DebugIO)

  dontTouch(devices)

  val cpu      = Module(new Core)
  val bridge   = Module(new BusBridge)
  val crossbar = Module(new BusCrossbar)

  cpu.io.imem <> bridge.imem
  cpu.io.dmem <> bridge.dmem
  cpu.io.mmio <> bridge.mmio
  cpu.io.irq <> irq

  crossbar_utils.connect(crossbar.ibus, bridge.ibus)
  crossbar_utils.connect(crossbar.dbus, bridge.dbus)
  crossbar_utils.connect(crossbar.mbus, bridge.mbus)

  for (i <- devices.indices)
    devices(i) <> crossbar.devices(i)

  debug <> cpu.io.debug
}
