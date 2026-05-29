package arch.node.div

import chisel3._

class DivCtrl extends Bundle {
  val is_signed = Bool()
  val is_rem    = Bool()
}
