package arch.configs.cpp.dsl

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
