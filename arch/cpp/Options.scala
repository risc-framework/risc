package arch.cpp

import java.nio.file.{ Path, Paths }

final case class CppCodegenOptions(
  baseDir: Path = Paths.get("build"),
  configNamespace: String = "demu::sys_def",
  isaNamespace: String = "demu::isa_def",
  configInclude: String = "sys_def.hh",
  isaInclude: String = "isa_def.hh",
  busBindingsInclude: String = "bus_bindings.hh"
)

final case class CppOutputPaths(
  configHeader: Path,
  isaHeader: Path,
  busBindingsHeader: Path
)
