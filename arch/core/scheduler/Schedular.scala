package arch.core.scheduler

import arch.configs._
import arch.core.fu._
import arch.core.scheduler.inorder._
import arch.core.scheduler.scoreboard._
import arch.core.uop._
import chisel3._
import chisel3.util.{ Decoupled, Valid, PriorityEncoder, log2Ceil }

abstract class Scheduler(implicit p: Parameters) extends Module {
  val dis_reqs = IO(Vec(p(IssueWidth), Flipped(Decoupled(new MicroOp))))
  val fu_reqs  = IO(Vec(p(NumFUs), Decoupled(new MicroOp)))
  val fu_done  = IO(Flipped(Vec(p(NumFUs), Valid(new FunctionalUnitResp))))

  val flush = IO(Input(Bool()))

  protected val numRegs = p(NumArchRegs)
  protected val RegIdxW = log2Ceil(p(NumArchRegs))

  protected val fuTypes =
    p(FunctionalUnits).map(_.`type`.index.U(p(FuTypeWidth).W))

  protected def isFuType(op: MicroOp, t: FunctionalUnitType): Bool =
    op.fu_type === t.index.U(p(FuTypeWidth).W)

  protected def isLoad(op: MicroOp): Bool =
    isFuType(op, FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD)

  protected def isStore(op: MicroOp): Bool =
    isFuType(op, FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST)

  protected def defaultFuReqs(): Unit =
    for (i <- 0 until p(NumFUs)) {
      fu_reqs(i).valid := false.B
      fu_reqs(i).bits  := 0.U.asTypeOf(new MicroOp)
    }

  protected def defaultDispatchReady(): Unit =
    for (w <- 0 until p(IssueWidth))
      dis_reqs(w).ready := false.B

  protected def fuMatchMask(op: MicroOp, used: Vec[Bool]): Vec[Bool] = {
    val mask = Wire(Vec(p(NumFUs), Bool()))

    for (i <- 0 until p(NumFUs))
      mask(i) := !used(i) && fu_reqs(i).ready && fuTypes(i) === op.fu_type

    mask
  }

  protected def selectFu(op: MicroOp, used: Vec[Bool]): (UInt, Bool) = {
    val mask = fuMatchMask(op, used)
    (PriorityEncoder(mask), mask.asUInt.orR)
  }

  protected def olderLaneAccepted(w: Int, accepted: Vec[Bool]): Bool =
    if (w == 0) true.B else !dis_reqs(w - 1).valid || accepted(w - 1)

  def bind(pool: FunctionalUnitPool): Unit = {
    pool.io.flush := flush

    for (i <- 0 until p(NumFUs)) {
      pool.io.req(i) <> fu_reqs(i)
      fu_done(i) := pool.io.done(i)
    }
  }
}

object Scheduler {
  def apply()(implicit p: Parameters): Scheduler =
    p(ScheduleType) match {
      case "in-order"   => Module(new Inorder)
      case "scoreboard" => Module(new Scoreboard)
      case other        => throw new IllegalArgumentException(s"Unknown ScheduleType: $other")
    }
}
