package arch.configs.runtime

import arch.configs.Parameters
import arch.isa.Isa
import arch.system.device.DeviceDescriptor

trait RuntimeBackend {
  def family: String
  def profile: String

  final def key: String =
    RuntimeBackend.key(family, profile)

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
}

object RuntimeBackend {
  def key(family: String, profile: String): String =
    s"${family.toLowerCase}:${profile.toLowerCase}"
}
