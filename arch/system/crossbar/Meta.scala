package arch.system.crossbar

import arch.configs._
import chisel3._
import vutils.graph.{ NodeDim, NodeDimensionImpl, NodeDimensionRegistry, NodePort, NodeType }

object BusCrossbarMeta {
  val Type    = NodeType("bus_crossbar")
  val IBUS    = NodePort[BusCrossbarIO, Bundle]("ibus", _.ibus)
  val DBUS    = NodePort[BusCrossbarIO, Bundle]("dbus", _.dbus)
  val MBUS    = NodePort[BusCrossbarIO, Bundle]("mbus", _.mbus)
  val DEVICES = NodePort[BusCrossbarIO, Vec[Bundle]]("devices", _.devices)
}

object BusCrossbarDims {
  val TYPE = NodeDim("type")
}

trait BusCrossbarTypeImpl extends NodeDimensionImpl {
  override def nodeType: NodeType = BusCrossbarMeta.Type
  override def dim: NodeDim       = BusCrossbarDims.TYPE
  override def name: String       = value

  def masterType(implicit p: Parameters): Bundle
  def slaveType(implicit p: Parameters): Bundle
  def addressMap(implicit p: Parameters): Seq[(Long, Long)]
  def createInterface(ibus: Bundle, dbus: Bundle, mbus: Bundle)(implicit p: Parameters): Vec[Bundle]
}

object BusCrossbarTypeFactory
    extends NodeDimensionRegistry[BusCrossbarTypeImpl](BusCrossbarMeta.Type, BusCrossbarDims.TYPE)

object BusCrossbarInit {
  val axil = impls.bus.axil.BusCrossbarAxilType
  val axif = impls.bus.axif.BusCrossbarAxifType
}
