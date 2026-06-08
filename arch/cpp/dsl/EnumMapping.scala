package arch.cpp.dsl

import arch.system.device.DeviceType
import vcache.repl.ReplPolicy

private[cpp] object CppEnumMapping {
  def deviceType(t: DeviceType): String =
    t match {
      case DeviceType.DEVICE_TYPE_SRAM    => "DEVICE_TYPE_SRAM"
      case DeviceType.DEVICE_TYPE_UART    => "DEVICE_TYPE_UART"
      case DeviceType.DEVICE_TYPE_IRH     => "DEVICE_TYPE_IRH"
      case DeviceType.DEVICE_TYPE_UNKNOWN => "DEVICE_TYPE_UNKNOWN"
    }

  def repl(p: ReplPolicy): String =
    p match {
      case ReplPolicy.Random    => "REPL_POLICY_RANDOM"
      case ReplPolicy.FIFO      => "REPL_POLICY_FIFO"
      case ReplPolicy.LFU       => "REPL_POLICY_LFU"
      case ReplPolicy.LRU       => "REPL_POLICY_LRU"
      case ReplPolicy.PseudoLRU => "REPL_POLICY_PSEUDO_LRU"
      case _                    => "REPL_POLICY_UNKNOWN"
    }

  def bus(s: String): String =
    s match {
      case "axil" => "BUS_TYPE_AXIL"
      case "axif" => "BUS_TYPE_AXIF"
      case _      => "BUS_TYPE_UNKNOWN"
    }
}
