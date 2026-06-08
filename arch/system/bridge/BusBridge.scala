package arch.system.bridge

import arch.configs._
import vcache.CachePortIO
import chisel3._
import vutils.graph.{ Node, NodeConfig, NodeSelector, NodeType }

class BusBridgeIO(implicit p: Parameters) extends Bundle {
  private val cfg = NodeConfig(
    selector = NodeSelector(
      BusBridgeDims.TYPE -> p(BusType)
    )
  )

  private val impl = BusBridgeTypeFactory.select(cfg)

  val imem = Flipped(
    new CachePortIO(Vec(p(IssueWidth), UInt(p(ILen).W)), p(L1ICacheParams))
  )
  val dmem = Flipped(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams)))
  val mmio = Flipped(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams)))

  val ibus = impl.busType
  val dbus = impl.busType
  val mbus = impl.busType
}

class BusBridge(implicit p: Parameters) extends Node(new BusBridgeIO) {
  private val cfg = NodeConfig(
    selector = NodeSelector(
      BusBridgeDims.TYPE -> p(BusType)
    )
  )

  override def nodeType: NodeType  = BusBridgeMeta.Type
  override def desiredName: String = s"bus_bridge_${cfg.selector.canonicalName}"

  private val impl = BusBridgeTypeFactory.select(cfg)

  dontTouch(io.imem)
  dontTouch(io.dmem)
  dontTouch(io.mmio)
  dontTouch(io.ibus)
  dontTouch(io.dbus)
  dontTouch(io.mbus)

  io.ibus <> impl.createBridgeReadOnly(Vec(p(IssueWidth), UInt(p(ILen).W)), io.imem, isMmio = false)
  io.dbus <> impl.createBridge(UInt(p(XLen).W), io.dmem, isMmio = false)
  io.mbus <> impl.createBridge(UInt(p(XLen).W), io.mmio, isMmio = true)
}
