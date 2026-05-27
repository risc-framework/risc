package arch.core.fu

import arch.core.uop._
import arch.configs._
import chisel3._
import chisel3.util.{ Decoupled, log2Ceil, switch, is }

sealed abstract class FunctionalUnitType(
  val index: Int,
  val cppName: String
)

object FunctionalUnitType {
  case object FUNCTIONAL_UNIT_TYPE_UNKNOWN extends FunctionalUnitType(0, "UNKNOWN")
  case object FUNCTIONAL_UNIT_TYPE_ALU     extends FunctionalUnitType(1, "ALU")
  case object FUNCTIONAL_UNIT_TYPE_MULT    extends FunctionalUnitType(2, "MULT")
  case object FUNCTIONAL_UNIT_TYPE_DIV     extends FunctionalUnitType(3, "DIV")
  case object FUNCTIONAL_UNIT_TYPE_LD      extends FunctionalUnitType(4, "LD")
  case object FUNCTIONAL_UNIT_TYPE_ST      extends FunctionalUnitType(5, "ST")
  case object FUNCTIONAL_UNIT_TYPE_BRU     extends FunctionalUnitType(6, "BRU")
  case object FUNCTIONAL_UNIT_TYPE_CSR     extends FunctionalUnitType(7, "CSR")

  val values: Seq[FunctionalUnitType] =
    Seq(
      FUNCTIONAL_UNIT_TYPE_UNKNOWN,
      FUNCTIONAL_UNIT_TYPE_ALU,
      FUNCTIONAL_UNIT_TYPE_MULT,
      FUNCTIONAL_UNIT_TYPE_DIV,
      FUNCTIONAL_UNIT_TYPE_LD,
      FUNCTIONAL_UNIT_TYPE_ST,
      FUNCTIONAL_UNIT_TYPE_BRU,
      FUNCTIONAL_UNIT_TYPE_CSR
    )
}

final case class FunctionalUnitDescriptor(
  name: String,
  `type`: FunctionalUnitType,
  impl: String = ""
)

class FunctionalUnitResp(implicit p: Parameters) extends Bundle {
  val result  = UInt(p(XLen).W)
  val rd      = UInt(log2Ceil(p(NumArchRegs)).W)
  val pc      = UInt(p(XLen).W)
  val instr   = UInt(p(ILen).W)
  val rob_tag = UInt(p(RobTagWidth).W)

  val is_bru        = Bool()
  val actual_taken  = Bool()
  val actual_target = UInt(p(XLen).W)

  val trap_req     = Bool()
  val trap_target  = UInt(p(XLen).W)
  val trap_ret     = Bool()
  val trap_ret_tgt = UInt(p(XLen).W)
}

class FunctionalUnitIO(implicit p: Parameters) extends Bundle {
  val req   = Flipped(Decoupled(new MicroOp))
  val resp  = Decoupled(new FunctionalUnitResp)
  val flush = Input(Bool())
}

abstract class FunctionalUnit(implicit p: Parameters) extends Module {
  val io = IO(new FunctionalUnitIO)

  def fuType: FunctionalUnitType

  protected def defaultResp(uop: MicroOp, result: UInt): FunctionalUnitResp = {
    val resp = Wire(new FunctionalUnitResp)

    resp.result  := result
    resp.rd      := uop.rd
    resp.pc      := uop.pc
    resp.instr   := uop.instr
    resp.rob_tag := uop.rob_tag

    resp.is_bru        := false.B
    resp.actual_taken  := false.B
    resp.actual_target := 0.U

    resp.trap_req     := false.B
    resp.trap_target  := 0.U
    resp.trap_ret     := false.B
    resp.trap_ret_tgt := 0.U

    resp
  }

  protected def zeroResp: FunctionalUnitResp =
    0.U.asTypeOf(new FunctionalUnitResp)

  protected def setNoResp(): Unit = {
    io.req.ready  := false.B
    io.resp.valid := false.B
    io.resp.bits  := zeroResp
  }
}

abstract class OneCycleFunctionalUnit(implicit p: Parameters) extends FunctionalUnit {
  private val uopReg     = Reg(new MicroOp)
  protected val validReg = RegInit(false.B)

  protected def execute(uop: MicroOp): UInt

  protected def augmentResp(resp: FunctionalUnitResp, uop: MicroOp): Unit = {}

  final protected def driveOneCycle(): Unit = {
    io.req.ready := !validReg || io.resp.fire

    when(io.flush) {
      validReg := false.B
    }.elsewhen(io.req.fire) {
      validReg := true.B
      uopReg   := io.req.bits
    }.elsewhen(io.resp.fire) {
      validReg := false.B
    }

    val resp = Wire(new FunctionalUnitResp)
    resp := defaultResp(uopReg, execute(uopReg))
    augmentResp(resp, uopReg)

    io.resp.valid := validReg && !io.flush
    io.resp.bits  := resp
  }
}

object BlockingFunctionalUnitState extends ChiselEnum {
  val IDLE, BUSY, DONE = Value
}

abstract class BlockingFunctionalUnit(implicit p: Parameters) extends FunctionalUnit {
  private val uopReg    = Reg(new MicroOp)
  private val resultReg = RegInit(0.U(p(XLen).W))
  private val state     = RegInit(BlockingFunctionalUnitState.IDLE)

  protected def engineReqReady: Bool
  protected def engineRespValid: Bool
  protected def engineResult: UInt

  protected def driveEngineReq(valid: Bool, op: MicroOp): Unit
  protected def driveEngineRespReady(ready: Bool): Unit
  protected def driveEngineKill(kill: Bool): Unit

  protected def augmentResp(resp: FunctionalUnitResp, uop: MicroOp): Unit = {}

  final protected def driveBlocking(): Unit = {
    val canAccept = state === BlockingFunctionalUnitState.IDLE && !io.flush

    io.req.ready := canAccept && engineReqReady

    driveEngineKill(io.flush)
    driveEngineReq(io.req.valid && canAccept, io.req.bits)
    driveEngineRespReady(state === BlockingFunctionalUnitState.BUSY && !io.flush)

    when(io.flush) {
      state := BlockingFunctionalUnitState.IDLE
    }.otherwise {
      switch(state) {
        is(BlockingFunctionalUnitState.IDLE) {
          when(io.req.fire) {
            uopReg := io.req.bits
            state  := BlockingFunctionalUnitState.BUSY
          }
        }

        is(BlockingFunctionalUnitState.BUSY) {
          when(engineRespValid) {
            resultReg := engineResult
            state     := BlockingFunctionalUnitState.DONE
          }
        }

        is(BlockingFunctionalUnitState.DONE) {
          when(io.resp.fire) {
            state := BlockingFunctionalUnitState.IDLE
          }
        }
      }
    }

    val resp = Wire(new FunctionalUnitResp)
    resp := defaultResp(uopReg, resultReg)
    augmentResp(resp, uopReg)

    io.resp.valid := state === BlockingFunctionalUnitState.DONE
    io.resp.bits  := resp
  }
}
