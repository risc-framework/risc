package arch.system

import arch.system.bridge.{ BusBridge, BusBridgeUtilsFactory }
import arch.system.crossbar.{ BusCrossbar, BusCrossbarUtilsFactory }
import arch.core.{ DebugIO, RiscCore }
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

  val debug = if (p(EnableDebug)) Some(IO(new DebugIO)) else None

  dontTouch(devices)

  val cpu      = Module(new RiscCore)
  val bridge   = Module(new BusBridge)
  val crossbar = Module(new BusCrossbar)

  cpu.imem <> bridge.imem
  cpu.dmem <> bridge.dmem
  cpu.mmio <> bridge.mmio
  cpu.irq <> irq

  crossbar_utils.connect(crossbar.ibus, bridge.ibus)
  crossbar_utils.connect(crossbar.dbus, bridge.dbus)
  crossbar_utils.connect(crossbar.mbus, bridge.mbus)

  for (i <- devices.indices)
    devices(i) <> crossbar.devices(i)

  if (debug.isDefined) {
    debug.get <> cpu.debug.get
  }
}
