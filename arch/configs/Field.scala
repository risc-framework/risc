package arch.configs

abstract class Field[T] private (
  val default: Option[T],
) {
  def this() = this(None)
  def this(default: T) = this(Some(default))

  def apply(): T = default.getOrElse(
    throw new IllegalArgumentException(s"Field ${this} has no default value")
  )
}
