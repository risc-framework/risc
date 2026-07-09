package arch.configs.runtime

import arch.configs.{ BusAddressMap, ISA, Parameters }
import arch.system.device.DeviceDescriptor
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }

object RuntimeCodegen {
  type Options = RuntimeCodegenOptions
  val Options = RuntimeCodegenOptions

  type OutputPaths = RuntimeOutputPaths
  val OutputPaths = RuntimeOutputPaths

  def emit(p: Parameters): Unit =
    emit(p, Options())

  def emit(p: Parameters, options: Options): Unit = {
    val isa = p(ISA)
    val backend = RuntimeBackendFactory.get(isa.family, options.profile)
    val outputDir = options.outputRoot.resolve(options.profile).resolve(isa.family)

    emit(
      p,
      OutputPaths(
        linkerScript = outputDir.resolve(options.linkerScriptName),
        startupSource = outputDir.resolve(options.startupSourceName)
      ),
      backend
    )
  }

  def emit(p: Parameters, paths: OutputPaths, backend: RuntimeBackend): Unit = {
    val isa = p(ISA)
    val imem = device(p, "imem")
    val dmem = device(p, "dmem")

    write(paths.linkerScript, backend.renderLinkerScript(p, isa, imem, dmem))
    write(paths.startupSource, backend.renderStartupSource(p, isa, imem, dmem))

    println(s"[RuntimeCodegen] generated linker script -> ${paths.linkerScript.normalize()}")
    println(s"[RuntimeCodegen] generated startup source -> ${paths.startupSource.normalize()}")
  }

  private def device(p: Parameters, name: String): DeviceDescriptor = {
    val devices = p(BusAddressMap)

    devices.find(_.name == name).getOrElse {
      throw new IllegalArgumentException(
        s"Cannot generate runtime: BusAddressMap does not contain required device '$name'"
      )
    }
  }

  private def write(path: Path, text: String): Unit = {
    val parent = path.getParent
    if (parent != null) {
      Files.createDirectories(parent)
    }

    Files.write(path, text.getBytes(StandardCharsets.UTF_8))
  }
}
