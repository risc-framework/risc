package arch.system.bridge

import arch.configs._
import vcache.{ CacheReq, CacheResp }
import chisel3._
import chisel3.util.DecoupledIO
import vutils.graph.NodeDims

object BusBridgeDims extends NodeDims("bus_bridge") {
  val TYPE = dim("type")
}

trait BusBridgeTypeImpl extends BusBridgeDims.TYPE.Impl {
  def busType(implicit p: Parameters): Bundle

  def createBridge[T <: Data](
    gen: T,
    req: DecoupledIO[CacheReq[T]],
    resp: DecoupledIO[CacheResp[T]],
    isMmio: Boolean = false
  )(implicit p: Parameters): Bundle

  def createBridgeReadOnly[T <: Data](
    gen: T,
    req: DecoupledIO[CacheReq[T]],
    resp: DecoupledIO[CacheResp[T]],
    isMmio: Boolean = false
  )(implicit p: Parameters): Bundle
}

object BusBridgeTypeFactory extends BusBridgeDims.TYPE.Registry[BusBridgeTypeImpl]

object BusBridgeInit {
  val axil = impls.bus.axil.BusBridgeAxilType.registered
  val axif = impls.bus.axif.BusBridgeAxifType.registered
}
