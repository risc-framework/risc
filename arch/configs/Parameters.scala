package arch.configs

class Parameters(
  val site: Map[Any, Any],
  val up: Option[Parameters] = None,
  val here: Map[Any, Any] = Map.empty
) {
  def apply[T](field: Field[T]): T =
    here
      .get(field)
      .orElse(
        site.get(field)
      )
      .orElse(
        up.flatMap(_.site.get(field))
      )
      .orElse(
        field.default
      )
      .getOrElse(
        throw new IllegalArgumentException(s"Parameter $field not found")
      )
      .asInstanceOf[T]

  def alter(f: (Any, Any, Any, Parameters) => Any): Parameters = {
    val newSite = site.map { case (k, v) =>
      k -> f(k, v, site, this)
    }
    new Parameters(newSite, up, here)
  }

  def alterPartial(pf: PartialFunction[Any, Any]): Parameters = {
    val newSite = site.map { case (k, v) =>
      k -> pf.applyOrElse(k, (_: Any) => v)
    }
    new Parameters(newSite, up, here)
  }

  def ++(other: Map[Any, Any]): Parameters =
    new Parameters(site ++ other, up, here)

  def copy(
    site: Map[Any, Any] = this.site,
    up: Option[Parameters] = this.up,
    here: Map[Any, Any] = this.here
  ): Parameters = new Parameters(site, up, here)
}

object Parameters {
  def empty: Parameters = new Parameters(Map.empty)

  def apply(
    site: Map[Any, Any],
    up: Option[Parameters] = None,
    here: Map[Any, Any] = Map.empty
  ): Parameters =
    new Parameters(site, up, here)
}
