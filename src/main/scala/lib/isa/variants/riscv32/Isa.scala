package arch.isa.variants.riscv32

import arch.isa._
import arch.isa.instructions._

abstract class Riscv32Isa extends FixedWidthIsa {
  final override def family: String =
    "riscv32"

  final override def xlen: Int =
    32

  final override def instrBits: Int =
    32

  final override def numArchRegs: Int =
    32

  final override def endian: String =
    Endianness.Little
}
