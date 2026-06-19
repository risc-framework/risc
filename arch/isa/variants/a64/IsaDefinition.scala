package arch.isa.variants.a64

import arch.isa._

abstract class A64IsaDefinition extends FixedWidthIsaDefinition {
  final override def family: String =
    "a64"

  final override def xlen: Int =
    64

  final override def instrBits: Int =
    32

  final override def numArchRegs: Int =
    32

  final override def endian: String =
    Endianness.Little
}
