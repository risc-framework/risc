package arch.cpp

import java.nio.file.{ Path, Paths }

final case class CppCodegenOptions(
  baseDir: Path = Paths.get("build"),
  configNamespace: String = "demu::sys_def",
  isaNamespace: String = "demu::isa_def",
  isaInclude: String = "isa_def.hh",
  emitMacros: Boolean = true
)

final case class CppOutputPaths(
  configHeader: Path,
  isaHeader: Path
)
