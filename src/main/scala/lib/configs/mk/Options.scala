package arch.configs.mk

import java.nio.file.Path

final case class MakeCodegenOptions(
  emitMakeAliases: Boolean = true
)

final case class MakeOutputPaths(
  config: Path
)
