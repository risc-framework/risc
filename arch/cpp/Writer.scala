package arch.cpp

private[cpp] final class CppWriter {
  private val sb = new StringBuilder
  private var level = 0

  def line(s: String = ""): Unit = {
    if (s.nonEmpty) {
      sb.append("  " * level)
      sb.append(s)
    }
    sb.append('\n')
  }

  def indent(body: => Unit): Unit = {
    level += 1
    body
    level -= 1
  }

  def namespace(name: String)(body: => Unit): Unit = {
    line(s"namespace $name {")
    indent(body)
    line(s"} // namespace $name")
  }

  def result: String =
    sb.result()
}
