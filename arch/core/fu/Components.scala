package arch.core.fu

import arch.configs._
import chisel3._

trait FunctionalUnitBuilder extends Utils {
  def fuType: FunctionalUnitType
  def build(implicit p: Parameters): FunctionalUnit
}

object FunctionalUnitFactory extends UtilsFactory[FunctionalUnitBuilder]("FunctionalUnit") {
  private def normalize(s: String): String = s.trim.toLowerCase

  private def defaultImplName(tpe: FunctionalUnitType): String = tpe.cppName.toLowerCase

  private def implName(desc: FunctionalUnitDescriptor): String =
    if (desc.impl.nonEmpty) normalize(desc.impl) else defaultImplName(desc.`type`)

  def get(desc: FunctionalUnitDescriptor): FunctionalUnitBuilder = {
    val key     = implName(desc)
    val builder = get(key).getOrElse {
      throw new NoSuchElementException(s"FunctionalUnit implementation '$key' for FU '${desc.name}' is not registered. Available: ${listAvailable().mkString(", ")}")
    }

    require(
      builder.fuType == desc.`type`,
      s"FunctionalUnit '${desc.name}' has type ${desc.`type`.cppName}, but implementation '$key' declares type ${builder.fuType.cppName}"
    )

    builder
  }

  def instantiate(desc: FunctionalUnitDescriptor)(implicit p: Parameters): FunctionalUnit = Module(get(desc).build)

  def instantiateAll()(implicit p: Parameters): Seq[FunctionalUnit] = p(FunctionalUnits).map(instantiate)
}

trait RegisteredFunctionalUnitBuilder extends RegisteredUtils[FunctionalUnitBuilder] {
  override def factory: UtilsFactory[FunctionalUnitBuilder] = FunctionalUnitFactory
}

object FunctionalUnitInit {
  val alu   = builtin.AluFUBuilder
  val mult  = builtin.MultFUBuilder
  val div   = builtin.DivFUBuilder
  val bru   = builtin.BruFUBuilder
  val load  = builtin.LoadFUBuilder
  val store = builtin.StoreFUBuilder
  val csr   = builtin.CsrFUBuilder
}
