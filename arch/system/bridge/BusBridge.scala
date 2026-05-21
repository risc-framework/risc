package arch.system.bridge

import arch.configs._
import vcache._
import chisel3._

class BusBridge(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(BusType)}_bridge"

  val utils = BusBridgeUtilsFactory.getOrThrow(p(BusType))

  val imem = IO(Flipped(new CachePortIO(Vec(p(IssueWidth), UInt(p(ILen).W)), p(L1ICacheParams))))
  val dmem = IO(Flipped(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams))))
  val mmio = IO(Flipped(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams))))

  val ibus = IO(utils.busType)
  val dbus = IO(utils.busType)
  val mbus = IO(utils.busType)

  dontTouch(imem)
  dontTouch(dmem)
  dontTouch(mmio)
  dontTouch(ibus)
  dontTouch(dbus)
  dontTouch(mbus)

  ibus <> utils.createBridgeReadOnly(Vec(p(IssueWidth), UInt(p(ILen).W)), imem, isMmio = false)
  dbus <> utils.createBridge(UInt(p(XLen).W), dmem, isMmio = false)
  mbus <> utils.createBridge(UInt(p(XLen).W), mmio, isMmio = true)
}
