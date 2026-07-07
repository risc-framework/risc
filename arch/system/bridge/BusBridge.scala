package arch.system.bridge

import arch.configs._
import arch.core.cpu.{ CpuDmemReq, CpuDmemResp, CpuImemReq, CpuImemResp }
import chisel3._
import vutils.graph.{ Node, NodeConfig, NodeSelector }

class BusBridge(implicit p: Parameters) extends Node[Parameters]("bus_bridge") {
  override protected def cfg: NodeConfig = NodeConfig(
    selector = NodeSelector(
      BusBridgeDims.TYPE -> p(BusType)
    )
  )

  private val impl = BusBridgeTypeFactory.select(cfg)

  val imemReq  = inD[CpuImemReq]
  val imemResp = outD[CpuImemResp]

  val dmemReq  = inD[CpuDmemReq]
  val dmemResp = outD[CpuDmemResp]

  val mmioReq  = inD[CpuDmemReq]
  val mmioResp = outD[CpuDmemResp]

  val ibus = rawWith(_ => impl.busType)
  val dbus = rawWith(_ => impl.busType)
  val mbus = rawWith(_ => impl.busType)

  ibus.io <> impl.createBridgeReadOnly(
    Vec(p(IssueWidth), UInt(p(ILen).W)),
    imemReq.in,
    imemResp.out,
    isMmio = false
  )

  dbus.io <> impl.createBridge(
    UInt(p(XLen).W),
    dmemReq.in,
    dmemResp.out,
    isMmio = false
  )

  mbus.io <> impl.createBridge(
    UInt(p(XLen).W),
    mmioReq.in,
    mmioResp.out,
    isMmio = true
  )
}
