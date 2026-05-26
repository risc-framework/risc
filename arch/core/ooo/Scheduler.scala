package arch.core.ooo

import arch.configs._
import chisel3._
import chisel3.util.{ Decoupled, Valid, log2Ceil }

abstract class Scheduler(implicit p: Parameters) extends Module {
  val dis_reqs = IO(Vec(p(IssueWidth), Flipped(Decoupled(new MicroOp))))
  val fu_reqs  = IO(Vec(p(FunctionalUnits).size, Decoupled(new MicroOp)))
  val fu_done  = IO(Flipped(Vec(p(FunctionalUnits).size, Valid(new FunctionalUnitResp))))

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

  protected def usesRs1(op: MicroOp): Bool =
    op.fu_type =/= FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR.index.U(p(FuTypeWidth).W)

  protected def usesRs2(op: MicroOp): Bool =
    op.fu_type === FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ALU.index.U(p(FuTypeWidth).W) ||
      op.fu_type === FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_MULT.index.U(p(FuTypeWidth).W) ||
      op.fu_type === FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_DIV.index.U(p(FuTypeWidth).W) ||
      op.fu_type === FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_BRU.index.U(p(FuTypeWidth).W) ||
      op.fu_type === FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST.index.U(p(FuTypeWidth).W)

  protected def defaultFuReqs(): Unit =
    for (i <- 0 until p(NumFUs)) {
      fu_reqs(i).valid := false.B
      fu_reqs(i).bits  := 0.U.asTypeOf(new MicroOp)
    }

  protected def defaultDispatchReady(): Unit =
    for (w <- 0 until p(IssueWidth))
      dis_reqs(w).ready := false.B
}

object Scheduler {
  def apply()(implicit p: Parameters): Scheduler =
    p(ScheduleType) match {
      case "in-order"   => Module(new Inorder)
      case "scoreboard" => Module(new Scoreboard)
      // case "tomasulo" => Module(new Tomasulo)
      case other        => throw new IllegalArgumentException(s"Unknown ScheduleType: $other")
    }
}
