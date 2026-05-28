package arch.core.fu

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
