package arch.system.crossbar

import arch.configs._
import chisel3._

class BusCrossbar(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(BusType)}_crossbar"

  val utils = BusCrossbarUtilsFactory.getOrThrow(p(BusType))

  val ibus    = IO(utils.masterType).suggestName("IBUS")
  val dbus    = IO(utils.masterType).suggestName("DBUS")
  val mbus    = IO(utils.masterType).suggestName("MBUS")
  val devices =
    IO(Vec(p(BusAddressMap).length, utils.slaveType)).suggestName(s"M_${p(BusType)}".toUpperCase)

  dontTouch(ibus)
  dontTouch(dbus)
  dontTouch(mbus)
  dontTouch(devices)

  val interface = utils.createInterface(ibus, dbus, mbus)

  for (i <- devices.indices)
    devices(i) <> interface(i)
}
