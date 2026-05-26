package arch.cpp

private[cpp] object CppLiteral {
  def braced(fields: Seq[String]): String =
    fields.mkString("{", ", ", "}")

  def boolLit(x: Boolean): String =
    if (x) "true" else "false"

  def u32Lit(x: Int): String =
    s"${x}u"

  def cstrLit(s: String): String =
    "\"" + escape(s) + "\""

  def enumLit(tpe: String, value: String): String =
    s"$tpe::$value"

  def hex32(x: BigInt): String = {
    val masked = x & BigInt("ffffffff", 16)
    "0x%08xu".format(masked.toLong)
  }

  def hex64(x: Long): String =
    "0x%016xull".format(x)

  def macroIdent(s: String): String =
    s.map {
        case c if c.isLetterOrDigit => c.toUpper
        case _                      => '_'
      }
      .mkString

  def emitMacroGuards(w: CppWriter, macros: Seq[(String, String)]): Unit = {
    val seen = scala.collection.mutable.LinkedHashSet.empty[String]

    macros.foreach { case (name, value) =>
      if (!seen.contains(name)) {
        seen += name

        w.line(s"#ifndef $name")
        w.line(s"#define $name $value")
        w.line("#endif")
        w.line()
      }
    }
  }

  private def escape(s: String): String =
    s.flatMap {
      case '\\' => "\\\\"
      case '"'  => "\\\""
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case c    => c.toString
    }
}
