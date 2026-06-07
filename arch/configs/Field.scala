package arch.configs

abstract class Field[T] private (
  private val deriveFn: Option[Parameters => T]
) {
  def this() = this(None)
  def this(derive: Parameters => T) = this(Some(derive))

  def hasDerive: Boolean = deriveFn.nonEmpty

  def derive(params: Parameters): Option[T] = deriveFn.map(_(params))

  override def toString: String =
    getClass.getSimpleName.stripSuffix("$")
}
