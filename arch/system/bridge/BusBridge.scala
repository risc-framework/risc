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

  val imemReq  = inD[CpuImemReq]
  val imemResp = outD[CpuImemResp]

  val dmemReq  = inD[CpuDmemReq]
  val dmemResp = outD[CpuDmemResp]

  val mmioReq  = inD[CpuDmemReq]
  val mmioResp = outD[CpuDmemResp]

  private val impl = BusBridgeTypeFactory.select(cfg)

  val ibus = IO(impl.busType)
  val dbus = IO(impl.busType)
  val mbus = IO(impl.busType)

  override def desiredName: String =
    s"bus_bridge_${cfg.selector.canonicalName}"

  dontTouch(imemReq.in)
  dontTouch(imemResp.out)
  dontTouch(dmemReq.in)
  dontTouch(dmemResp.out)
  dontTouch(mmioReq.in)
  dontTouch(mmioResp.out)

  dontTouch(ibus)
  dontTouch(dbus)
  dontTouch(mbus)

  ibus <> impl.createBridgeReadOnly(
    Vec(p(IssueWidth), UInt(p(ILen).W)),
    imemReq.in,
    imemResp.out,
    isMmio = false
  )

  dbus <> impl.createBridge(
    UInt(p(XLen).W),
    dmemReq.in,
    dmemResp.out,
    isMmio = false
  )

  mbus <> impl.createBridge(
    UInt(p(XLen).W),
    mmioReq.in,
    mmioResp.out,
    isMmio = true
  )
}
