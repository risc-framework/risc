package arch.node.fupool

import arch.node.uop.MicroOp
import arch.configs._
import chisel3._
import chisel3.util.{ Decoupled, Valid, log2Ceil }

sealed abstract class FunctionalUnitType(
  val index: Int,
  val cppName: String
)

// NOTE: in order to keep the original code strcuture unchanged
// object FunctionalUnitType {
//   case object FUNCTIONAL_UNIT_TYPE_UNKNOWN extends FunctionalUnitType(0, "UNKNOWN")
//   case object FUNCTIONAL_UNIT_TYPE_ALU     extends FunctionalUnitType(1, "ALU")
//   case object FUNCTIONAL_UNIT_TYPE_MULT    extends FunctionalUnitType(2, "MULT")
//   case object FUNCTIONAL_UNIT_TYPE_DIV     extends FunctionalUnitType(3, "DIV")
//   case object FUNCTIONAL_UNIT_TYPE_LD      extends FunctionalUnitType(4, "LD")
//   case object FUNCTIONAL_UNIT_TYPE_ST      extends FunctionalUnitType(5, "ST")
//   case object FUNCTIONAL_UNIT_TYPE_BRU     extends FunctionalUnitType(6, "BRU")
//   case object FUNCTIONAL_UNIT_TYPE_CSR     extends FunctionalUnitType(7, "CSR")
//
//   val values: Seq[FunctionalUnitType] =
//     Seq(
//       FUNCTIONAL_UNIT_TYPE_UNKNOWN,
//       FUNCTIONAL_UNIT_TYPE_ALU,
//       FUNCTIONAL_UNIT_TYPE_MULT,
//       FUNCTIONAL_UNIT_TYPE_DIV,
//       FUNCTIONAL_UNIT_TYPE_LD,
//       FUNCTIONAL_UNIT_TYPE_ST,
//       FUNCTIONAL_UNIT_TYPE_BRU,
//       FUNCTIONAL_UNIT_TYPE_CSR
//     )
// }

object FunctionalUnitType {
  val FUNCTIONAL_UNIT_TYPE_UNKNOWN = arch.core.fu.FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_UNKNOWN
  val FUNCTIONAL_UNIT_TYPE_ALU     = arch.core.fu.FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ALU
  val FUNCTIONAL_UNIT_TYPE_MULT    = arch.core.fu.FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_MULT
  val FUNCTIONAL_UNIT_TYPE_DIV     = arch.core.fu.FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_DIV
  val FUNCTIONAL_UNIT_TYPE_LD      = arch.core.fu.FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD
  val FUNCTIONAL_UNIT_TYPE_ST      = arch.core.fu.FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST
  val FUNCTIONAL_UNIT_TYPE_BRU     = arch.core.fu.FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_BRU
  val FUNCTIONAL_UNIT_TYPE_CSR     = arch.core.fu.FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR

  val values: Seq[arch.core.fu.FunctionalUnitType] = arch.core.fu.FunctionalUnitType.values
}

final case class FunctionalUnitDescriptor(
  name: String,
  `type`: FunctionalUnitType,
  impl: String = ""
)

class FuResp(implicit p: Parameters) extends Bundle {
  val result  = UInt(p(XLen).W)
  val rd      = UInt(log2Ceil(p(NumArchRegs)).W)
  val pc      = UInt(p(XLen).W)
  val instr   = UInt(p(ILen).W)
  val rob_tag = UInt(p(RobTagWidth).W)
}

class FuIO(implicit p: Parameters) extends Bundle {
  val req   = Flipped(Decoupled(new MicroOp))
  val resp  = Decoupled(new FuResp)
  val flush = Input(Bool())
}

class FuPoolFuIO(implicit p: Parameters) extends Bundle {
  val req   = Vec(p(NumFUs), Flipped(Decoupled(new MicroOp)))
  val done  = Output(Vec(p(NumFUs), Valid(new FuResp)))
  val flush = Input(Bool())
}
