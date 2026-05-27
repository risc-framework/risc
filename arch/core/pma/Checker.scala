package arch.core.pma

import arch.system.DeviceType
import arch.configs._
import chisel3._

class PmaChecker(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_pma_checker"

  val addr      = IO(Input(UInt(p(XLen).W)))
  val valid     = IO(Output(Bool()))
  val readable  = IO(Output(Bool()))
  val writable  = IO(Output(Bool()))
  val cacheable = IO(Output(Bool()))

  val hits = p(BusAddressMap).map { d =>
    val hit = (addr >= d.base.U(p(XLen).W)) && (addr < (d.base + d.size).U(p(XLen).W))
    (d.`type`, hit)
  }

  val is_sram = hits.filter(_._1 == DeviceType.DEVICE_TYPE_SRAM).map(_._2).reduce(_ || _)
  val is_uart = hits.filter(_._1 == DeviceType.DEVICE_TYPE_UART).map(_._2).reduce(_ || _)
  val is_irh  = hits.filter(_._1 == DeviceType.DEVICE_TYPE_IRH).map(_._2).reduce(_ || _)

  valid     := is_sram || is_uart || is_irh
  readable  := is_sram || is_uart || is_irh
  writable  := is_sram || is_uart || is_irh
  cacheable := is_sram
}

object PmaChecker {
  def apply(addr: UInt)(implicit p: Parameters): (Bool, Bool, Bool, Bool) = {
    val pma = Module(new PmaChecker)
    pma.addr := addr
    (pma.valid, pma.readable, pma.writable, pma.cacheable)
  }
}
