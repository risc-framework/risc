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

    def mergedRanges(deviceType: DeviceType): Seq[(Long, Long)] = {
      p(BusAddressMap)
        .filter(_.`type` == deviceType)
        .map(d => d.base -> (d.base + d.size))
        .sortBy(_._1)
        .foldLeft(Vector.empty[(Long, Long)]) { case (ranges, (base, end)) =>
          ranges.lastOption match {
            case Some((lastBase, lastEnd)) if base <= lastEnd =>
              ranges.init :+ (lastBase -> math.max(lastEnd, end))
            case _ =>
              ranges :+ (base -> end)
          }
        }
    }

    def rangeHit(base: Long, end: Long): Bool = {
      val size       = end - base
      val powerOfTwo = size > 0 && (size & (size - 1)) == 0
      val aligned    = powerOfTwo && (base & (size - 1)) == 0

      if (aligned) {
        val offsetBits = java.lang.Long.numberOfTrailingZeros(size)
        if (offsetBits >= p(XLen))
          true.B
        else
          addr(p(XLen) - 1, offsetBits) ===
            (base >>> offsetBits).U((p(XLen) - offsetBits).W)
      } else {
        addr >= base.U(p(XLen).W) && addr < end.U(p(XLen).W)
      }
    }

    def anyHit(deviceType: DeviceType): Bool = {
      val xs = mergedRanges(deviceType).map { case (base, end) =>
        rangeHit(base, end)
      }
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
