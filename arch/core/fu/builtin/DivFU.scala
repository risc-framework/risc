package arch.core.fu.builtin

import arch.core.fu._
import arch.core.uop.MicroOp
import arch.core.div.{ Div, DivUtilsFactory }
import arch.configs._
import chisel3._

class DivFU(implicit p: Parameters) extends BlockingFunctionalUnit {
  override def desiredName: String = s"${p(ISA).name}_div_fu"

  override def fuType: FunctionalUnitType =
    FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_DIV

  private val div       = Module(new Div)
  private val div_utils = DivUtilsFactory.getOrThrow(p(ISA).name)

  override protected def engineReqReady: Bool =
    div.io.req.ready

  override protected def engineRespValid: Bool =
    div.io.resp.valid

  override protected def engineResult: UInt =
    div.io.resp.bits.result

  override protected def driveEngineReq(valid: Bool, op: MicroOp): Unit = {
    div.io.req.valid     := valid
    div.io.req.bits.src1 := op.rs1_data
    div.io.req.bits.src2 := op.rs2_data
    div.io.req.bits.ctrl := div_utils.decode(op.uop)
  }

  override protected def driveEngineRespReady(ready: Bool): Unit =
    div.io.resp.ready := ready

  override protected def driveEngineKill(kill: Bool): Unit =
    div.io.kill := kill

  driveBlocking()
}

object DivFUBuilder extends RegisteredFUBuilder {
  override lazy val utils: FUBuilder = new FUBuilder {
    override def name: String                                  = "div"
    override def fuType: FunctionalUnitType                    = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_DIV
    override def build(implicit p: Parameters): FunctionalUnit = new DivFU
  }
}
