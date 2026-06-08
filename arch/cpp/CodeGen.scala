package arch.cpp

import arch.configs.Parameters
import dsl.CppWriter
import gen.{ CppIsaSchema, CppSysSchema, CppBusBindingsSchema, CppRetireBindingsSchema }
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path, Paths }

object CppCodegen {
  type Options = CppCodegenOptions
  val Options = CppCodegenOptions

  type OutputPaths = CppOutputPaths
  val OutputPaths = CppOutputPaths

  def emit(
    p: Parameters,
    configHeader: String,
    isaHeader: String,
    busBindingsHeader: String,
    retireBindingsHeader: String
  ): Unit =
    emit(
      p,
      OutputPaths(
        configHeader = Paths.get(configHeader),
        isaHeader = Paths.get(isaHeader),
        busBindingsHeader = Paths.get(busBindingsHeader),
        retireBindingsHeader = Paths.get(retireBindingsHeader)
      ),
      Options()
    )

  def emit(
    p: Parameters,
    configHeader: String,
    isaHeader: String,
    busBindingsHeader: String,
    retireBindingsHeader: String,
    options: Options
  ): Unit =
    emit(
      p,
      OutputPaths(
        configHeader = Paths.get(configHeader),
        isaHeader = Paths.get(isaHeader),
        busBindingsHeader = Paths.get(busBindingsHeader),
        retireBindingsHeader = Paths.get(retireBindingsHeader)
      ),
      options
    )

  def emit(
    p: Parameters,
    paths: OutputPaths,
    options: Options = Options()
  ): Unit = {
    val configPath = resolveOutputPath(paths.configHeader)
    val isaPath    = resolveOutputPath(paths.isaHeader)
    val busPath    = resolveOutputPath(paths.busBindingsHeader)
    val retirePath = resolveOutputPath(paths.retireBindingsHeader)

    write(isaPath, renderIsaHeader(p, options))
    write(configPath, renderConfigHeader(p, options))
    write(busPath, renderBusBindingsHeader(p, options))
    write(retirePath, renderRetireBindingsHeader(p, options))

    println(s"[CppCodegen] generated config header         -> $configPath")
    println(s"[CppCodegen] generated ISA header            -> $isaPath")
    println(s"[CppCodegen] generated bus bindings header   -> $busPath")
    println(s"[CppCodegen] generated retire bindings header -> $retirePath")
  }

  def renderIsaHeader(p: Parameters, options: Options = Options()): String = {
    val w = new CppWriter

    w.line("#pragma once")
    w.line()
    w.line(s"""#include "${CppIsaSchema.verilatedHeader(p, options)}"""")
    w.line("#include <array>")
    w.line("#include <cstdint>")
    w.line("#include <string_view>")
    w.line()

    w.namespace(options.isaNamespace) {
      CppIsaSchema.emitTypes(w)
      CppIsaSchema.emitValues(w, p, options)
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

    w.result
  }

  def renderBusBindingsHeader(
    p: Parameters,
    options: Options = Options()
  ): String = {
    val w = new CppWriter

    w.line("#pragma once")
    w.line()
    w.line(s"""#include "${options.isaInclude}"""")
    w.line(s"""#include "${options.configInclude}"""")
    w.line("#include <cstddef>")
    w.line("#include <cstdint>")
    w.line()

    w.namespace(options.busBindingsNamespace) {
      CppBusBindingsSchema.emit(w, p)
    }

    w.result
  }

  def renderRetireBindingsHeader(
    p: Parameters,
    options: Options = Options()
  ): String = {
    val w = new CppWriter

    w.line("#pragma once")
    w.line()
    w.line(s"""#include "${options.isaInclude}"""")
    w.line(s"""#include "${options.configInclude}"""")
    w.line("#include <cstddef>")
    w.line("#include <cstdint>")
    w.line()

    w.namespace(options.retireBindingsNamespace) {
      CppRetireBindingsSchema.emit(w, p)
    }

    w.result
  }

  private def resolveOutputPath(path: Path): Path =
    path.normalize()

  private def write(path: Path, text: String): Unit = {
    val parent = path.getParent
    if (parent != null) {
      Files.createDirectories(parent)
    }

    Files.write(path, text.getBytes(StandardCharsets.UTF_8))
  }
}
