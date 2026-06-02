package arch.core.pma

import chisel3._

class PmaCheckResult extends Bundle {
  val valid     = Bool()
  val readable  = Bool()
  val writable  = Bool()
  val cacheable = Bool()
}
