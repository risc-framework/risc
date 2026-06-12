package arch.system.crossbar.impls.bus.axil

import arch.configs._
import arch.system.crossbar._
import vamba.axi4.lite._
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._

object BusCrossbarAxilType extends RegisteredNodeUtils[BusCrossbarTypeImpl] {
  override def utils: BusCrossbarTypeImpl = new BusCrossbarTypeImpl {
    override def value: String = "axil"

    private def axiParams(implicit p: Parameters): Axi4LiteParams =
      Axi4LiteParams(addrWidth = p(XLen), dataWidth = p(XLen))

    private def ranges(implicit p: Parameters): Seq[Axi4LiteAddressRange] =
      p(BusAddressMap).map(desc => Axi4LiteAddressRange.fromSize(desc.base, desc.size))

    private def cfg(implicit p: Parameters): Axi4LiteFabricConfig = {
      val d = p(BusCrossbarFifoDepthPerClient)

      Axi4LiteFabricConfig(
        decoderRouteDepth = d,
        arbiterAwDepth = d,
        arbiterWDepth = d,
        arbiterArDepth = d,
        arbiterBRouteDepth = 3 * d,
        arbiterRRouteDepth = 3 * d,
        queuePipe = p(BusRouteQueuePipe),
      )
    }

    override def masterType(implicit p: Parameters): Bundle =
      new Axi4LiteSlavePort(axiParams)

    override def slaveType(implicit p: Parameters): Bundle =
      new Axi4LiteMasterPins(axiParams)

    override def addressMap(implicit p: Parameters): Seq[(Long, Long)] =
      p(BusAddressMap).map(desc => (desc.base, desc.base + desc.size))

    override def createInterface(
      ibus: Bundle,
      dbus: Bundle,
      mbus: Bundle
    )(implicit p: Parameters): Vec[Bundle] = {
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
  }

  override def registry: NodeDimensionRegistry[BusCrossbarTypeImpl] =
    BusCrossbarTypeFactory
}
