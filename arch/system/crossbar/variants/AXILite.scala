package arch.system.crossbar

import arch.configs._
import vamba.axi4.lite._
import chisel3._

object AXILiteCrossbarUtils extends RegisteredUtils[BusCrossbarUtils] {
  override def utils: BusCrossbarUtils = new BusCrossbarUtils {
    override def name: String = "axil"

    private def axiParams: Axi4LiteParams =
      Axi4LiteParams(addrWidth = p(XLen), dataWidth = p(XLen))

    private def ranges: Seq[Axi4LiteAddressRange] =
      p(BusAddressMap).map(desc => Axi4LiteAddressRange.fromSize(desc.base, desc.size))

    private def cfg: Axi4LiteFabricConfig = {
      val d = p(BusCrossbarFifoDepthPerClient)

      Axi4LiteFabricConfig(
        decoderRouteDepth = d,
        arbiterAwDepth = d,
        arbiterWDepth = d,
        arbiterArDepth = d,
        arbiterBRouteDepth = 3 * d,
        arbiterRRouteDepth = 3 * d,
        queuePipe = true,
      )
    }

    override def masterType: Bundle =
      new Axi4LiteSlavePort(axiParams)

    override def slaveType: Bundle =
      new Axi4LiteMasterPins(axiParams)

    override def addressMap: Seq[(Long, Long)] =
      p(BusAddressMap).map(desc => (desc.base, desc.base + desc.size))

    override def createInterface(ibus: Bundle, dbus: Bundle, mbus: Bundle): Vec[Bundle] = {
      val crossbar = Module(
        new Axi4LiteCrossbar(
          p = axiParams,
          numMasters = 3,
          addressMap = ranges,
          cfg = cfg
        )
      )

      val interface = Wire(Vec(ranges.length, new Axi4LiteMasterPins(axiParams)))

      crossbar.io.masters(0) <> ibus.asInstanceOf[Axi4LiteSlavePort]
      crossbar.io.masters(1) <> dbus.asInstanceOf[Axi4LiteSlavePort]
      crossbar.io.masters(2) <> mbus.asInstanceOf[Axi4LiteSlavePort]

      for (i <- interface.indices)
        Axi4LitePins.connectMasterPins(interface(i), crossbar.io.slaves(i))

      interface.asInstanceOf[Vec[Bundle]]
    }

    override def connect(ext: Bundle, inner: Bundle): Unit =
      Axi4Lite.connect(
        inner.asInstanceOf[Axi4LiteMasterPort],
        ext.asInstanceOf[Axi4LiteSlavePort]
      )
  }

  override def factory: UtilsFactory[BusCrossbarUtils] =
    BusCrossbarUtilsFactory
}
