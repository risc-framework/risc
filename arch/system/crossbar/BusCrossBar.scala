package arch.system.crossbar

import arch.configs._
import chisel3._
import vutils.graph.{ Node, NodeConfig, NodeSelector, NodeType }

class BusCrossbarIO(implicit p: Parameters) extends Bundle {
  private val cfg = NodeConfig(
    selector = NodeSelector(
      BusCrossbarDims.TYPE -> p(BusType)
    )
  )

  private val impl = BusCrossbarTypeFactory.select(cfg)

  val ibus    = impl.masterType
  val dbus    = impl.masterType
  val mbus    = impl.masterType
  val devices = Vec(p(BusAddressMap).length, impl.slaveType)
}

class BusCrossbar(implicit p: Parameters) extends Node(new BusCrossbarIO) {
  private val cfg = NodeConfig(
    selector = NodeSelector(
      BusCrossbarDims.TYPE -> p(BusType)
    )
  )

  override def nodeType: NodeType  = BusCrossbarMeta.Type
  override def desiredName: String = s"bus_crossbar_${cfg.selector.canonicalName}"

  private val impl = BusCrossbarTypeFactory.select(cfg)

  dontTouch(io.ibus)
  dontTouch(io.dbus)
  dontTouch(io.mbus)
  dontTouch(io.devices)

  private val interface = impl.createInterface(io.ibus, io.dbus, io.mbus)

  for (i <- 0 until p(BusAddressMap).length)
    io.devices(i) <> interface(i)
}
