package arch.system.soc

import arch.configs._
import arch.core.cpu.{ Cpu, CpuDebugInfo }
import arch.core.csr.InterruptLines
import arch.system.bridge.BusBridge
import arch.system.crossbar.{ BusCrossbar, BusCrossbarDims, BusCrossbarTypeFactory }
import chisel3._
import vutils.graph.{ Node, NodeConfig, NodeSelector }

class Soc(implicit p: Parameters) extends Node[Parameters]("soc") {
  override protected def cfg: NodeConfig = NodeConfig(
    selector = NodeSelector(
      BusCrossbarDims.TYPE -> p(BusType)
    )
  )

  override def desiredName: String = "soc"

  private val crossbarImpl = BusCrossbarTypeFactory.select(cfg)

  val irq     = in[InterruptLines]
  val debug   = out[CpuDebugInfo]
  val devices = rawWith(_ => Vec(p(BusAddressMap).length, crossbarImpl.slaveType))

  private val cpu      = subnode(new Cpu)
  private val bridge   = subnode(new BusBridge)
  private val crossbar = subnode(new BusCrossbar)

  link(
    cpu.imemReq      -> bridge.imemReq,
    bridge.imemResp  -> cpu.imemResp,
    cpu.dmemReq      -> bridge.dmemReq,
    bridge.dmemResp  -> cpu.dmemResp,
    cpu.mmioReq      -> bridge.mmioReq,
    bridge.mmioResp  -> cpu.mmioResp,
    bridge.ibus      -> crossbar.ibus,
    bridge.dbus      -> crossbar.dbus,
    bridge.mbus      -> crossbar.mbus,
    crossbar.devices -> devices
  )

  cpu.irq.in := irq.in
  debug.out  := cpu.debug.out
}
