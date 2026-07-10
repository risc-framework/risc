package arch.configs.runtime

import scala.collection.mutable.LinkedHashMap

object RuntimeBackendFactory {
  private val registry = LinkedHashMap.empty[RuntimeBackendKey, RuntimeBackend]

  def register(backend: RuntimeBackend): RuntimeBackend = {
    val key = backend.key.normalized

    require(
      !registry.contains(key),
      s"Runtime backend '${backend.family}/${backend.profile}' already registered"
    )

    registry(key) = backend
    backend
  }

  def fromString(family: String, profile: String): Option[RuntimeBackend] =
    registry.get(RuntimeBackendKey(family = family, profile = profile).normalized)

  def get(family: String, profile: String): RuntimeBackend =
    fromString(family, profile).getOrElse {
      val availableText =
        if (available.isEmpty) "<none>"
        else available.map(backend => s"${backend.family}/${backend.profile}").mkString(", ")

      throw new Exception(
        s"Unknown runtime backend: '$family/$profile'. Available: $availableText"
      )
    }

  def available: Seq[RuntimeBackend] =
    registry.values.toSeq
}

object RuntimeBackendInit {
  val riscv32BareMetal: RuntimeBackend =
    impls.riscv32.baremetal.Riscv32BareMetalRuntimeBackend.registered
}
