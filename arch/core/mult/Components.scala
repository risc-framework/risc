package arch.core.mult

import chisel3._

class MultCtrl extends Bundle {
  val a_signed = Bool()
  val b_signed = Bool()
  val high     = Bool()
}
