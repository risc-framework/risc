package arch.configs.runtime

import scala.collection.mutable.LinkedHashMap

object RuntimeBackendFactory {
  private val registry = LinkedHashMap.empty[String, RuntimeBackend]

  def register(backend: RuntimeBackend): RuntimeBackend = {
    val key = backend.key
    require(!registry.contains(key), s"Runtime backend '$key' already registered")
    registry(key) = backend
    backend
  }

  def get(family: String, profile: String): RuntimeBackend = {
    val key = RuntimeBackend.key(family, profile)

    registry.getOrElse(
      key,
      throw new IllegalArgumentException(
        s"Unknown runtime backend '$key'. Available: ${available.map(_.key).mkString(", ")}"
      )
    )
  }

  def available: Seq[RuntimeBackend] =
    registry.values.toSeq
}
