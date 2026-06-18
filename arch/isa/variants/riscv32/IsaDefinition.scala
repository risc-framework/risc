package arch.isa

abstract class Riscv32IsaDefinition extends FixedWidthIsaDefinition {
  final override def family: String =
    "riscv"

  final override def xlen: Int =
    32

  final override def instrBits: Int =
    32

  final override def numArchRegs: Int =
    32

  final override def endian: String =
    Endianness.Little
}
