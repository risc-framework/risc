package arch.cpp.dsl

import arch.configs.{ Field, Parameters }
import CppLiteral._

private[cpp] object CppValueDsl {
  def str(name: String, value: Parameters => String): CppValue =
    ScalarValue("std::string_view", name, p => cstrLit(value(p)))

  def bool(name: String, field: Field[Boolean]): CppValue =
    ScalarValue("bool", name, p => boolLit(p(field)))

  def bool(name: String, value: Parameters => Boolean): CppValue =
    ScalarValue("bool", name, p => boolLit(value(p)))

  def u32(name: String, field: Field[Int]): CppValue =
    ScalarValue("uint32_t", name, p => u32Lit(p(field)))

  def u32(name: String, value: Parameters => Int): CppValue =
    ScalarValue("uint32_t", name, p => u32Lit(value(p)))

  def u64(name: String, field: Field[Long]): CppValue =
    ScalarValue("uint64_t", name, p => hex64(p(field)))

  def cppEnum(
    tpe: String,
    name: String,
    value: Parameters => String
  ): CppValue =
    ScalarValue(tpe, name, p => enumLit(tpe, value(p)))

  def alias(tpe: String, name: String, expr: String): CppValue =
    AliasValue(tpe, name, expr)

  def typeAlias(name: String, expr: String): CppValue =
    TypeAliasValue(name, _ => expr)

  def typeAlias(name: String, expr: Parameters => String): CppValue =
    TypeAliasValue(name, expr)

  def struct(
    tpe: String,
    name: String,
    fields: Parameters => Seq[String]
  ): CppValue =
    StructValue(tpe, name, fields)

  def array(
    name: String,
    elemType: String,
    sizeName: String,
    values: Parameters => Seq[String]
  ): CppValue =
    ArrayValue(name, elemType, sizeName, values)
}
