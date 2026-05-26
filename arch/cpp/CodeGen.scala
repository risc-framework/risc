package arch.cpp

import arch.configs.Parameters
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path, Paths }

object CppCodegen {
  type Options = CppCodegenOptions
  val Options = CppCodegenOptions

  type OutputPaths = CppOutputPaths
  val OutputPaths = CppOutputPaths

  def emit(p: Parameters, configHeader: String, isaHeader: String): Unit =
    emit(
      p,
      OutputPaths(
        configHeader = Paths.get(configHeader),
        isaHeader = Paths.get(isaHeader)
      ),
      Options()
    )

  def emit(
    p: Parameters,
    configHeader: String,
    isaHeader: String,
    options: Options
  ): Unit =
    emit(
      p,
      OutputPaths(
        configHeader = Paths.get(configHeader),
        isaHeader = Paths.get(isaHeader)
      ),
      options
    )

  def emit(
    p: Parameters,
    paths: OutputPaths,
    options: Options = Options()
  ): Unit = {
    val isaPath    = resolveOutputPath(options, paths.isaHeader)
    val configPath = resolveOutputPath(options, paths.configHeader)

    write(isaPath, renderIsaHeader(p, options))
    write(configPath, renderConfigHeader(p, options))

    println(s"[CppCodegen] generated ISA header    -> $isaPath")
    println(s"[CppCodegen] generated config header -> $configPath")
  }

  def emitIsa(p: Parameters, isaHeader: String): Unit =
    emitIsa(p, Paths.get(isaHeader), Options())

  def emitIsa(
    p: Parameters,
    isaHeader: Path,
    options: Options = Options()
  ): Unit = {
    val path = resolveOutputPath(options, isaHeader)
    write(path, renderIsaHeader(p, options))
    println(s"[CppCodegen] generated ISA header -> $path")
  }

  def emitConfig(p: Parameters, configHeader: String): Unit =
    emitConfig(p, Paths.get(configHeader), Options())

  def emitConfig(
    p: Parameters,
    configHeader: Path,
    options: Options = Options()
  ): Unit = {
    val path = resolveOutputPath(options, configHeader)
    write(path, renderConfigHeader(p, options))
    println(s"[CppCodegen] generated config header -> $path")
  }

  def renderIsaHeader(p: Parameters, options: Options = Options()): String = {
    val w = new CppWriter

    w.line("#pragma once")
    w.line()
    w.line("#include <array>")
    w.line("#include <cstdint>")
    w.line("#include <string_view>")
    w.line()

    w.namespace(options.isaNamespace) {
      CppIsaSchema.emitTypes(w)
      CppIsaSchema.emitValues(w, p)
    }

    if (options.emitMacros) {
      w.line()
      CppIsaSchema.emitMacros(w, p)
    }

    w.result
  }

  def renderConfigHeader(p: Parameters, options: Options = Options()): String = {
    val w = new CppWriter

    w.line("#pragma once")
    w.line()
    w.line(s"""#include "${options.isaInclude}"""")
    w.line("#include <array>")
    w.line("#include <cstdint>")
    w.line("#include <string_view>")
    w.line()

    w.namespace(options.configNamespace) {
      CppSysSchema.emitTypes(w)
      CppSysSchema.emitValues(w, p, options)
    }

    if (options.emitMacros) {
      w.line()
      CppSysSchema.emitMacros(w, p)
    }

    w.result
  }

  private def resolveOutputPath(options: Options, path: Path): Path =
    if (path.isAbsolute) {
      path.normalize()
    } else {
      options.baseDir.resolve(path).normalize()
    }

  private def write(path: Path, text: String): Unit = {
    val parent = path.getParent
    if (parent != null) {
      Files.createDirectories(parent)
    }

    Files.write(path, text.getBytes(StandardCharsets.UTF_8))
  }
}
