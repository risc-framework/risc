package arch.configs.runtime

import java.nio.file.Path

final case class RuntimeCodegenOptions(
  profile: String = "bare-metal",
  outputRoot: Path = Path.of("build/runtime"),
  linkerScriptName: String = "linker.ld",
  startupSourceName: String = "start.S"
)

final case class RuntimeOutputPaths(
  linkerScript: Path,
  startupSource: Path
)
