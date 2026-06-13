package arch.core.csr.impls.file.rv32im

import arch.core.csr._
import arch.core.csr.impls.file.rv32i.Rv32iCsrFile
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }

object Rv32imCsrFile extends RegisteredNodeUtils[CsrFileImpl] {
  override def utils: CsrFileImpl = new CsrFileImpl {
    private val rv32i = Rv32iCsrFile.utils

    override def value: String =
      "rv32im"

    override def addrWidth: Int =
      rv32i.addrWidth

    override def opWidth: Int =
      rv32i.opWidth

    override def table(implicit p: arch.configs.Parameters): Seq[(CsrRegister, CsrUpdateBehavior)] =
      rv32i.table

    override def command(
      instr: chisel3.UInt,
      uop: chisel3.UInt,
      rs1: chisel3.UInt,
      rd: chisel3.UInt,
      rs1Data: chisel3.UInt,
      imm: chisel3.UInt
    )(implicit p: arch.configs.Parameters): CsrFileCmd =
      rv32i.command(instr, uop, rs1, rd, rs1Data, imm)

    override def write(old: chisel3.UInt, cmd: CsrFileCmd)(implicit
      p: arch.configs.Parameters
    ): chisel3.UInt =
      rv32i.write(old, cmd)
  }

  override def registry: NodeDimensionRegistry[CsrFileImpl] =
    CsrFileFactory
}
