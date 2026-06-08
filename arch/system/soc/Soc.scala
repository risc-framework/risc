package arch.system.soc

import arch.configs._
import arch.core.cpu.{ Cpu, DebugIO }
import arch.core.csr.InterruptLines
import arch.system.bridge.BusBridge
import arch.system.crossbar.{ BusCrossbar, BusCrossbarTypeFactory, BusCrossbarDims }
import vutils.graph.{ NodeConfig, NodeSelector }
import chisel3._

class Soc(implicit p: Parameters) extends Module {
  private val cfg = NodeConfig(
    selector = NodeSelector(
      BusCrossbarDims.TYPE -> p(BusType)
    )
  )

  override def desiredName: String = "soc"

  private val crossbarImpl = BusCrossbarTypeFactory.select(cfg)

  val devices = IO(Vec(p(BusAddressMap).length, crossbarImpl.slaveType))
    .suggestName(s"M_${p(BusType)}".toUpperCase)
  val irq     = IO(Input(new InterruptLines))
  val debug   = IO(Output(new DebugIO))

  private val cpu      = Module(new Cpu)
  private val bridge   = Module(new BusBridge)
  private val crossbar = Module(new BusCrossbar)

  dontTouch(devices)

  cpu.io.imem <> bridge.io.imem
  cpu.io.dmem <> bridge.io.dmem
  cpu.io.mmio <> bridge.io.mmio
  cpu.io.irq := irq

  crossbar.io.ibus <> bridge.io.ibus
  crossbar.io.dbus <> bridge.io.dbus
  crossbar.io.mbus <> bridge.io.mbus

  for (i <- 0 until p(BusAddressMap).length)
    devices(i) <> crossbar.io.devices(i)

  debug <> cpu.io.debug
}
