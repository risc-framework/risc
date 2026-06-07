package arch.cpp

import arch.system.device.DeviceType
import arch.core.fupool.FunctionalUnitType
import vcache.repl.ReplPolicy

private[cpp] object CppEnumMapping {
  def fuType(t: FunctionalUnitType): String =
    t match {
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ALU     => "FUNCTIONAL_UNIT_TYPE_ALU"
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_MULT    => "FUNCTIONAL_UNIT_TYPE_MULT"
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_DIV     => "FUNCTIONAL_UNIT_TYPE_DIV"
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD      => "FUNCTIONAL_UNIT_TYPE_LD"
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST      => "FUNCTIONAL_UNIT_TYPE_ST"
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_BRU     => "FUNCTIONAL_UNIT_TYPE_BRU"
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR     => "FUNCTIONAL_UNIT_TYPE_CSR"
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_UNKNOWN => "FUNCTIONAL_UNIT_TYPE_UNKNOWN"
    }

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
