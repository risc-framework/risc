package arch.system.crossbar.impls.bus.axif

import arch.configs._
import arch.system.crossbar._
import vamba.axi4.full._
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._

object BusCrossbarAxifType extends RegisteredNodeUtils[BusCrossbarTypeImpl] {
  override def utils: BusCrossbarTypeImpl = new BusCrossbarTypeImpl {
    override def value: String = "axif"

    private def axiParams(implicit p: Parameters): Axi4FullParams =
      Axi4FullParams(
        addrWidth = p(XLen),
        dataWidth = p(XLen),
        idWidth = 4,
        userWidth = 0
      )

    private def ranges(implicit p: Parameters): Seq[Axi4FullAddressRange] =
      p(BusAddressMap).map(desc => Axi4FullAddressRange.fromSize(desc.base, desc.size))

    private def cfg(implicit p: Parameters): Axi4FullFabricConfig = {
      val d             = p(BusCrossbarFifoDepthPerClient)
      val maxLineBytes  = p(L1ICacheLineSize).max(p(L1DCacheLineSize))
      val maxBurstBeats = (maxLineBytes / p(BytesPerWord)).max(1)

      Axi4FullFabricConfig(
        decoderWriteRouteDepth = 3 * d,
        decoderReadRouteDepth = 3 * d,
        arbiterAwDepth = d,
        arbiterWDepth = d * maxBurstBeats,
        arbiterArDepth = d,
        arbiterWRouteDepth = 3 * d,
        arbiterBRouteDepth = 3 * d,
        arbiterRRouteDepth = 3 * d,
        queuePipe = p(BusRouteQueuePipe),
      )
    }

    override def masterType(implicit p: Parameters): Bundle =
      new Axi4FullSlavePort(axiParams)

    override def slaveType(implicit p: Parameters): Bundle =
      new Axi4FullMasterPins(axiParams)

    override def addressMap(implicit p: Parameters): Seq[(Long, Long)] =
      p(BusAddressMap).map(desc => (desc.base, desc.base + desc.size))

    override def createInterface(
      ibus: Bundle,
      dbus: Bundle,
      mbus: Bundle
    )(implicit p: Parameters): Vec[Bundle] = {
      val crossbar = Module(
        new Axi4FullCrossbar(
          p = axiParams,
          numMasters = 3,
          addressMap = ranges,
          cfg = cfg
        )
      )

      val interface = Wire(Vec(ranges.length, new Axi4FullMasterPins(axiParams)))

      crossbar.io.masters(0) <> ibus.asInstanceOf[Axi4FullSlavePort]
      crossbar.io.masters(1) <> dbus.asInstanceOf[Axi4FullSlavePort]
      crossbar.io.masters(2) <> mbus.asInstanceOf[Axi4FullSlavePort]

      for (i <- interface.indices)
        Axi4FullPins.connectMasterPins(interface(i), crossbar.io.slaves(i))

      interface.asInstanceOf[Vec[Bundle]]
    }
  }

  override def registry: NodeDimensionRegistry[BusCrossbarTypeImpl] =
    BusCrossbarTypeFactory
}
