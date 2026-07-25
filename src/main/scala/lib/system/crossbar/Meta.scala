package arch.system.crossbar

import arch.configs._
import chisel3._
import vutils.graph.NodeDims

object BusCrossbarDims extends NodeDims("bus_crossbar") {
  val TYPE = dim("type")
}

trait BusCrossbarTypeImpl extends BusCrossbarDims.TYPE.Impl {
  def masterType(implicit p: Parameters): Bundle
  def slaveType(implicit p: Parameters): Bundle
  def addressMap(implicit p: Parameters): Seq[(Long, Long)]
  def createInterface(ibus: Bundle, dbus: Bundle, mbus: Bundle)(implicit p: Parameters): Vec[Bundle]
}

object BusCrossbarTypeFactory extends BusCrossbarDims.TYPE.Registry[BusCrossbarTypeImpl]

object BusCrossbarInit {
  val axil = impls.bus.axil.BusCrossbarAxilType.registered
  val axif = impls.bus.axif.BusCrossbarAxifType.registered
}
