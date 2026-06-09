package arch.core.scheduler

import chisel3._

class SchedulerExceptionReq extends Bundle {
  val flush = Bool()
}
