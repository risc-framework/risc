package arch.core.pma

import arch.system.device.DeviceType
import arch.configs._
import vutils.graph.{ NodeType, NodeDim, NodeDimensionImpl, NodeDimensionRegistry }
import chisel3._

object PmaMeta {
  val Type = NodeType("pma")
}

object PmaDims {
  val MODE = NodeDim("mode")
}

trait PmaModeImpl extends NodeDimensionImpl {
  override def nodeType: NodeType = PmaMeta.Type
  override def dim: NodeDim       = PmaDims.MODE
  override def name: String       = value

  def check(addr: UInt)(implicit p: Parameters): PmaCheckResult = {
    val result = Wire(new PmaCheckResult)

    val hits = p(BusAddressMap).map { d =>
      val hit = (addr >= d.base.U(p(XLen).W)) && (addr < (d.base + d.size).U(p(XLen).W))
      (d.`type`, hit)
    }

    val is_sram = hits.filter(_._1 == DeviceType.DEVICE_TYPE_SRAM).map(_._2).reduce(_ || _)
    val is_uart = hits.filter(_._1 == DeviceType.DEVICE_TYPE_UART).map(_._2).reduce(_ || _)
    val is_irh  = hits.filter(_._1 == DeviceType.DEVICE_TYPE_IRH).map(_._2).reduce(_ || _)

    result.valid     := is_sram || is_uart || is_irh
    result.readable  := is_sram || is_uart || is_irh
    result.writable  := is_sram || is_uart || is_irh
    result.cacheable := is_sram

    result
  }
}

object PmaModeFactory extends NodeDimensionRegistry[PmaModeImpl](PmaMeta.Type, PmaDims.MODE)

object PmaInit {
  val default = impls.mode.default.PmaDefaultMode
}
