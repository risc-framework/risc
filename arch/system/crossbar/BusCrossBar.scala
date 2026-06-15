package arch.system.crossbar

import arch.configs._
import chisel3._
import vutils.graph.{ Node, NodeConfig, NodeSelector }

class BusCrossbar(implicit p: Parameters) extends Node[Parameters]("bus_crossbar") {
  override protected def cfg: NodeConfig = NodeConfig(
    selector = NodeSelector(
      BusCrossbarDims.TYPE -> p(BusType)
    )
  )

  private val impl = BusCrossbarTypeFactory.select(cfg)

  val ibus    = IO(impl.masterType)
  val dbus    = IO(impl.masterType)
  val mbus    = IO(impl.masterType)
  val devices = IO(Vec(p(BusAddressMap).length, impl.slaveType))

  dontTouch(ibus)
  dontTouch(dbus)
  dontTouch(mbus)
  dontTouch(devices)

  private val interface = impl.createInterface(ibus, dbus, mbus)

  for (i <- 0 until p(BusAddressMap).length)
    devices(i) <> interface(i)
}
