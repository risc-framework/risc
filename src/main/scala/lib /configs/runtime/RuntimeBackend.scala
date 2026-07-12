package arch.configs.runtime

import arch.configs.Parameters
import arch.isa.Isa
import arch.system.device.DeviceDescriptor

final case class RuntimeBackendKey(
  family: String,
  profile: String
) {
  require(family.nonEmpty, "runtime backend family must not be empty")
  require(profile.nonEmpty, "runtime backend profile must not be empty")

  def normalized: RuntimeBackendKey =
    RuntimeBackendKey(
      family = family.toLowerCase,
      profile = profile.toLowerCase
    )

  override def toString: String =
    s"$family/$profile"
}

abstract class RuntimeBackend {
  def family: String
  def profile: String

  def renderLinkerScript(
    p: Parameters,
    isa: Isa,
    imem: DeviceDescriptor,
    dmem: DeviceDescriptor
  ): String

  def renderStartupSource(
    p: Parameters,
    isa: Isa,
    imem: DeviceDescriptor,
    dmem: DeviceDescriptor
  ): String

  protected def validate(): Unit = {
    require(family.nonEmpty, "runtime backend family must not be empty")
    require(profile.nonEmpty, s"runtime backend '$family' profile must not be empty")
  }

  final def key: RuntimeBackendKey =
    RuntimeBackendKey(family = family, profile = profile)

  final lazy val registered: RuntimeBackend = {
    validate()
    RuntimeBackendFactory.register(this)
  }
}
