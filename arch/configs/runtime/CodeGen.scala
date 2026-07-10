package arch.configs.runtime

import arch.configs._
import arch.system.device.{ DeviceDescriptor, DeviceType }
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path, Paths }

object RuntimeCodegen {
  def emit(
    p: Parameters,
    root: String = "build/runtime",
    profile: String = "bare-metal"
  ): Unit = {
    val isa        = p(ISA)
    val backend    = RuntimeBackendFactory.get(isa.family, profile)
    val imem       = findDevice(p, "imem", DeviceType.DEVICE_TYPE_SRAM)
    val dmem       = findDevice(p, "dmem", DeviceType.DEVICE_TYPE_SRAM)
    val runtimeDir = Paths.get(root, backend.profile, backend.family)

    write(
      runtimeDir.resolve("linker.ld"),
      backend.renderLinkerScript(
        p = p,
        isa = isa,
        imem = imem,
        dmem = dmem
      )
    )

    write(
      runtimeDir.resolve("start.S"),
      backend.renderStartupSource(
        p = p,
        isa = isa,
        imem = imem,
        dmem = dmem
      )
    )
  }

  private def findDevice(
    p: Parameters,
    name: String,
    deviceType: DeviceType
  ): DeviceDescriptor = {
    val devices = p(BusAddressMap)

    devices
      .find(device => device.name == name && device.`type` == deviceType)
      .getOrElse {
        val available = devices.map(device => s"${device.name}:${device.`type`}").mkString(", ")

        throw new Exception(
          s"Cannot find runtime device '$name' of type '$deviceType'. Available: $available"
        )
      }
  }

  private def write(path: Path, content: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
  }
}
