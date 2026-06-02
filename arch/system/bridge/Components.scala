package arch.system.bridge

import chisel3._

object Axi4BridgeState extends ChiselEnum {
  val Idle, AR, R, AW, W, B = Value
}
