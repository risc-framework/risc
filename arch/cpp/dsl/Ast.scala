package arch.cpp.dsl

import arch.configs.Parameters
import CppLiteral._

sealed private[cpp] trait CppDecl {
  def emit(w: CppWriter): Unit
}

final private[cpp] case class EnumDecl(
  name: String,
  underlying: String,
  values: Seq[(String, Int)]
) extends CppDecl {
  override def emit(w: CppWriter): Unit = {
    w.line(s"enum class $name : $underlying {")
    w.indent {
      values.foreach { case (n, v) =>
        w.line(s"$n = $v,")
      }
    }
    w.line("};")
  }
}

final private[cpp] case class StructDecl(
  name: String,
  fields: Seq[(String, String)]
) extends CppDecl {
  override def emit(w: CppWriter): Unit = {
    w.line(s"struct $name {")
    w.indent {
      fields.foreach { case (tpe, name) =>
        w.line(s"$tpe $name;")
      }
    }
    w.line("};")
  }
}

sealed private[cpp] trait CppValue {
  def emit(w: CppWriter, p: Parameters): Unit
}

final private[cpp] case class ScalarValue(
  tpe: String,
  name: String,
  value: Parameters => String
) extends CppValue {
  override def emit(w: CppWriter, p: Parameters): Unit =
    w.line(s"inline constexpr $tpe $name = ${value(p)};")
}

final private[cpp] case class AliasValue(
  tpe: String,
  name: String,
  expr: String
) extends CppValue {
  override def emit(w: CppWriter, p: Parameters): Unit =
    w.line(s"inline constexpr $tpe $name = $expr;")
}

final private[cpp] case class TypeAliasValue(
  name: String,
  expr: Parameters => String
) extends CppValue {
  override def emit(w: CppWriter, p: Parameters): Unit =
    w.line(s"using $name = ${expr(p)};")
}

final private[cpp] case class StructValue(
  tpe: String,
  name: String,
  fields: Parameters => Seq[String]
) extends CppValue {
  override def emit(w: CppWriter, p: Parameters): Unit =
    w.line(s"inline constexpr $tpe $name = ${braced(fields(p))};")
}

final private[cpp] case class ArrayValue(
  name: String,
  elemType: String,
  sizeName: String,
  values: Parameters => Seq[String]
) extends CppValue {
  override def emit(w: CppWriter, p: Parameters): Unit = {
    w.line(s"inline constexpr std::array<$elemType, $sizeName> $name = {{")
    w.indent {
      values(p).foreach(v => w.line(s"$v,"))
    }
    w.line("}};")
  }
}
