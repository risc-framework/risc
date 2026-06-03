package arch.core.scheduler

import arch.core.fupool.FuResp
import arch.core.fu.FunctionalUnitType
import arch.core.uop.MicroOp
import arch.configs._
import chisel3._
import chisel3.util.{ Decoupled, PriorityEncoder, Valid, log2Ceil }

class SchedulerDispatchIO(implicit p: Parameters) extends Bundle {
  val reqs = Vec(p(IssueWidth), Flipped(Decoupled(new MicroOp)))
}

class SchedulerFuIO(implicit p: Parameters) extends Bundle {
  val reqs = Vec(p(NumFUs), Decoupled(new MicroOp))
  val done = Flipped(Vec(p(NumFUs), Valid(new FuResp)))
}

class SchedulerCtrlIO extends Bundle {
  val flush = Input(Bool())
}

final class SchedulerContext(val io: SchedulerIO)(implicit p: Parameters) {
  val numRegs = p(NumArchRegs)
  val regIdxW = log2Ceil(p(NumArchRegs))

  val fuTypes =
    p(FunctionalUnits).map(_.`type`.index.U(p(FuTypeWidth).W))

  def isFuType(op: MicroOp, t: FunctionalUnitType): Bool =
    op.fu_type === t.index.U(p(FuTypeWidth).W)

  def isLoad(op: MicroOp): Bool =
    isFuType(op, FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD)

  def isStore(op: MicroOp): Bool =
    isFuType(op, FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST)

  def defaultFuReqs(): Unit =
    for (i <- 0 until p(NumFUs)) {
      io.fu.reqs(i).valid := false.B
      io.fu.reqs(i).bits  := 0.U.asTypeOf(new MicroOp)
    }

  def defaultDispatchReady(): Unit =
    for (w <- 0 until p(IssueWidth))
      io.dispatch.reqs(w).ready := false.B

  def fuMatchMask(op: MicroOp, used: Vec[Bool]): Vec[Bool] = {
    val mask = Wire(Vec(p(NumFUs), Bool()))

    for (i <- 0 until p(NumFUs))
      mask(i) := !used(i) && io.fu.reqs(i).ready && fuTypes(i) === op.fu_type

    mask
  }

  def selectFu(op: MicroOp, used: Vec[Bool]): (UInt, Bool) = {
    val mask = fuMatchMask(op, used)
    (PriorityEncoder(mask), mask.asUInt.orR)
  }

  def olderLaneAccepted(w: Int, accepted: Vec[Bool]): Bool =
    if (w == 0) true.B else !io.dispatch.reqs(w - 1).valid || accepted(w - 1)
}
