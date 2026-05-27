package arch.core.fu.builtin

import arch.core.fu._
import arch.core.uop.MicroOp
import arch.core.mult.{ MultUtilsFactory, Mult }
import arch.configs._
import chisel3._

class MultFU(implicit p: Parameters) extends BlockingFunctionalUnit {
  override def desiredName: String = s"${p(ISA).name}_mult_fu"

  override def fuType: FunctionalUnitType =
    FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_MULT

  private val mult       = Module(new Mult)
  private val mult_utils = MultUtilsFactory.getOrThrow(p(ISA).name)

  override protected def engineReqReady: Bool =
    mult.io.req.ready

  override protected def engineRespValid: Bool =
    mult.io.resp.valid

  override protected def engineResult: UInt =
    mult.io.resp.bits.result

  override protected def driveEngineReq(valid: Bool, op: MicroOp): Unit = {
    mult.io.req.valid     := valid
    mult.io.req.bits.src1 := op.rs1_data
    mult.io.req.bits.src2 := op.rs2_data
    mult.io.req.bits.ctrl := mult_utils.decode(op.uop)
  }

  override protected def driveEngineRespReady(ready: Bool): Unit =
    mult.io.resp.ready := ready

  override protected def driveEngineKill(kill: Bool): Unit =
    mult.io.kill := kill

  driveBlocking()
}

object MultFUBuilder extends RegisteredFunctionalUnitBuilder {
  override lazy val utils: FunctionalUnitBuilder = new FunctionalUnitBuilder {
    override def name: String                                  = "mult"
    override def fuType: FunctionalUnitType                    = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_MULT
    override def build(implicit p: Parameters): FunctionalUnit = new MultFU
  }
}
