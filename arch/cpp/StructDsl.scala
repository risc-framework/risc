package arch.cpp

import CppTypeDsl._

private[cpp] object CppStructDsl {
  final case class StructField(tpe: CppType, name: String, init: String = "{}") {
    def emit(w: CppWriter): Unit = {
      val sep = if (tpe.value.endsWith("*")) "" else " "
      w.line(s"${tpe.value}$sep$name$init;")
    }
  }

  final case class StructSpec(
    name: String,
    fields: Seq[StructField],
    emitValidMethod: Boolean = false
  ) {
    def emit(w: CppWriter): Unit = {
      w.line(s"struct $name {")
      w.indent {
        fields.foreach(_.emit(w))

        if (emitValidMethod) {
          w.line()
          emitValid(w)
        }
      }
      w.line("};")
    }

    private def emitValid(w: CppWriter): Unit = {
      val names  = fields.map(_.name)
      val groups = names.grouped(5).toSeq

      w.line("[[nodiscard]] auto valid() const noexcept -> bool {")
      w.indent {
        groups.zipWithIndex.foreach { case (group, idx) =>
          val prefix = if (idx == 0) "return " else "       "
          val suffix = if (idx == groups.size - 1) ";" else " &&"
          w.line(prefix + group.mkString(" && ") + suffix)
        }
      }
      w.line("}")
    }
  }

  def field(tpe: CppType, name: String): StructField =
    StructField(tpe, name)
}
