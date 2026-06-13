package arch.core.csr.impls.file.rv32im

import arch.configs._
import arch.core.csr._
import arch.core.csr.impls.file.rv32i.CsrRv32iFile
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._

object CsrRv32imFile extends RegisteredNodeUtils[CsrFileImpl] {
  override def utils: CsrFileImpl = new CsrFileImpl {
    private val rv32i = CsrRv32iFile.utils

    override def value: String  = "rv32im"
    override def addrWidth: Int = rv32i.addrWidth
    override def opWidth: Int   = rv32i.opWidth

    override def table(implicit p: Parameters): Seq[(CsrRegister, CsrUpdateBehavior)] =
      rv32i.table.map {
        case (reg, behavior) if reg.name == "misa" =>
          (CsrRegister(reg.name, reg.addr, 0x40001100L, reg.writable, reg.readable), behavior)
        case other                                 => other
      }

    override def command(
      instr: UInt,
      uop: UInt,
      rs1: UInt,
      rd: UInt,
      rs1Data: UInt,
      imm: UInt
    )(implicit p: Parameters): CsrFileCmd =
      rv32i.command(instr, uop, rs1, rd, rs1Data, imm)

    override def write(old: UInt, cmd: CsrFileCmd)(implicit p: Parameters): UInt =
      rv32i.write(old, cmd)
  }

  override def registry: NodeDimensionRegistry[CsrFileImpl] =
    CsrFileFactory
}
