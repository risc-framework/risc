package arch.cpp

import java.nio.file.Path

final case class CppCodegenOptions(
  configNamespace: String = "demu::sys_def",
  isaNamespace: String = "demu::isa_def",
  busBindingsNamespace: String = "demu::bus_def",
  retireBindingsNamespace: String = "demu::retire_def",
  configInclude: String = "sys_def.hh",
  isaInclude: String = "isa_def.hh",
  busBindingsInclude: String = "bus_bindings.hh",
  retireBindingsInclude: String = "retire_bindings.hh"
)

final case class CppOutputPaths(
  configHeader: Path,
  isaHeader: Path,
  busBindingsHeader: Path,
  retireBindingsHeader: Path
)
