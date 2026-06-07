package arch.configs

import scala.collection.mutable

final class Parameters private (
  val site: Map[Field[_], Any],
  val up: Option[Parameters],
  val here: Map[Field[_], Any]
) {
  private val cache     = mutable.LinkedHashMap.empty[Field[_], Any]
  private val resolving = mutable.LinkedHashSet.empty[Field[_]]

  private def explicitValue(field: Field[_]): Option[Any] =
    here.get(field).orElse(site.get(field)).orElse(up.flatMap(_.explicitValue(field)))

  def apply[T](field: Field[T]): T =
    explicitValue(field) match {
      case Some(value) => value.asInstanceOf[T]
      case None        => derivedValue(field)
    }

  private def derivedValue[T](field: Field[T]): T =
    cache.get(field) match {
      case Some(value) => value.asInstanceOf[T]
      case None        =>
        if (resolving.contains(field)) {
          throw new IllegalArgumentException(
            s"Recursive parameter derivation detected while resolving '$field'"
          )
        }

        resolving += field

        val value = field.derive(this).getOrElse {
          throw new IllegalArgumentException(s"Required parameter '$field' was not manually set")
        }

        resolving -= field
        cache(field) = value
        value
    }

  def containsExplicit(field: Field[_]): Boolean =
    explicitValue(field).nonEmpty

  def contains(field: Field[_]): Boolean =
    containsExplicit(field) || field.hasDerive

  def alter[T](field: Field[T], value: T): Parameters =
    new Parameters(site + (field -> value), up, here)

  def alter(values: (Field[_], Any)*): Parameters =
    new Parameters(site ++ values.toMap, up, here)

  def alterPartial(pf: PartialFunction[Field[_], Any]): Parameters = {
    val newSite = site.map { case (field, value) =>
      field -> pf.applyOrElse(field, (_: Field[_]) => value)
    }
    new Parameters(newSite, up, here)
  }

  def ++(other: Map[Field[_], Any]): Parameters =
    new Parameters(site ++ other, up, here)

  def withHere(values: (Field[_], Any)*): Parameters =
    new Parameters(site, up, here ++ values.toMap)

  def withParent(parent: Parameters): Parameters =
    new Parameters(site, Some(parent), here)

  def requireExplicit(fields: Seq[Field[_]]): Unit = {
    val missing = fields.filterNot(containsExplicit)

    if (missing.nonEmpty) {
      throw new IllegalArgumentException(
        s"Missing required manual parameters: ${missing.map(_.toString).mkString(", ")}"
      )
    }
  }

  def materialize(fields: Seq[Field[_]]): Parameters = {
    val values = fields.map(field => field -> this(field.asInstanceOf[Field[Any]])).toMap
    new Parameters(site ++ values, up, here)
  }

  def copy(
    site: Map[Field[_], Any] = this.site,
    up: Option[Parameters] = this.up,
    here: Map[Field[_], Any] = this.here
  ): Parameters = new Parameters(site, up, here)
}

object Parameters {
  def empty: Parameters = new Parameters(Map.empty, None, Map.empty)

  def apply(
    site: Map[Field[_], Any],
    up: Option[Parameters] = None,
    here: Map[Field[_], Any] = Map.empty
  ): Parameters = new Parameters(site, up, here)

  def of(values: (Field[_], Any)*): Parameters =
    new Parameters(values.toMap, None, Map.empty)
}
