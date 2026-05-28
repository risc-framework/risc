package arch.system.bridge

import arch.configs._
import vcache.CachePortIO
import chisel3._

trait BusBridgeUtils extends Utils {
  def busType: Bundle
  def createBridge[T <: Data](gen: T, memory: CachePortIO[T], isMmio: Boolean = false): Bundle
  def createBridgeReadOnly[T <: Data](
    gen: T,
    memory: CachePortIO[T],
    isMmio: Boolean = false
  ): Bundle
}

object BusBridgeUtilsFactory extends UtilsFactory[BusBridgeUtils]("BusBridge")

object BusBridgeInit {
  val axil = AXILiteBridgeUtils
  val axif = AXIFullBridgeUtils
}
