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

  val ibus    = rawWith(_ => impl.masterType)
  val dbus    = rawWith(_ => impl.masterType)
  val mbus    = rawWith(_ => impl.masterType)
  val devices = rawWith(_ => Vec(p(BusAddressMap).length, impl.slaveType))

  private val interface = impl.createInterface(ibus.io, dbus.io, mbus.io)

  for (i <- 0 until p(BusAddressMap).length)
    devices.io(i) <> interface(i)
}
