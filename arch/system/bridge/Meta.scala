package arch.system.bridge

import arch.configs._
import vcache.CachePortIO
import chisel3._
import vutils.graph.{ NodeDim, NodeDimensionImpl, NodeDimensionRegistry, NodePort, NodeType }

object BusBridgeMeta {
  val Type = NodeType("bus_bridge")
  val IMEM = NodePort[BusBridgeIO, CachePortIO[Vec[UInt]]]("imem", _.imem)
  val DMEM = NodePort[BusBridgeIO, CachePortIO[UInt]]("dmem", _.dmem)
  val MMIO = NodePort[BusBridgeIO, CachePortIO[UInt]]("mmio", _.mmio)
  val IBUS = NodePort[BusBridgeIO, Bundle]("ibus", _.ibus)
  val DBUS = NodePort[BusBridgeIO, Bundle]("dbus", _.dbus)
  val MBUS = NodePort[BusBridgeIO, Bundle]("mbus", _.mbus)
}

object BusBridgeDims {
  val TYPE = NodeDim("type")
}

trait BusBridgeTypeImpl extends NodeDimensionImpl {
  override def nodeType: NodeType = BusBridgeMeta.Type
  override def dim: NodeDim       = BusBridgeDims.TYPE
  override def name: String       = value

  def busType(implicit p: Parameters): Bundle

  def createBridge[T <: Data](
    gen: T,
    memory: CachePortIO[T],
    isMmio: Boolean = false
  )(implicit p: Parameters): Bundle

  def createBridgeReadOnly[T <: Data](
    gen: T,
    memory: CachePortIO[T],
    isMmio: Boolean = false
  )(implicit p: Parameters): Bundle
}

object BusBridgeTypeFactory
    extends NodeDimensionRegistry[BusBridgeTypeImpl](BusBridgeMeta.Type, BusBridgeDims.TYPE)

object BusBridgeInit {
  val axil = impls.bus.axil.BusBridgeAxilType
  val axif = impls.bus.axif.BusBridgeAxifType
}
