package arch.core.pma

import arch.system.device.DeviceType
import arch.configs._
import vutils.graph.NodeDims
import chisel3._

object PmaDims extends NodeDims("pma") {
  val MODE = dim("mode")
}

trait PmaModeImpl extends PmaDims.MODE.Impl {
  def check(addr: UInt)(implicit p: Parameters): PmaCheckResult = {
    val result = Wire(new PmaCheckResult)

    val hits = p(BusAddressMap).map { d =>
      val hit = (addr >= d.base.U(p(XLen).W)) && (addr < (d.base + d.size).U(p(XLen).W))
      (d.`type`, hit)
    }

    def anyHit(deviceType: DeviceType): Bool = {
      val xs = hits.filter(_._1 == deviceType).map(_._2)
      if (xs.isEmpty) false.B else xs.reduce(_ || _)
    }

    val is_sram = anyHit(DeviceType.DEVICE_TYPE_SRAM)
    val is_uart = anyHit(DeviceType.DEVICE_TYPE_UART)
    val is_irh  = anyHit(DeviceType.DEVICE_TYPE_IRH)

    result.valid     := is_sram || is_uart || is_irh
    result.readable  := is_sram || is_uart || is_irh
    result.writable  := is_sram || is_uart || is_irh
    result.cacheable := is_sram

    result
  }
}

object PmaModeFactory extends PmaDims.MODE.Registry[PmaModeImpl]

object PmaInit {
  val default = impls.mode.default.PmaDefaultMode.registered
}
