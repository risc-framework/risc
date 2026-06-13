package arch.core.csr.impls.file.rv32i

import arch.configs._
import arch.core.csr._
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.{ BitPat, MuxLookup }

trait Rv32iCsrUopConsts {
  private def cat(bps: BitPat*): BitPat = bps.reduce(_ ## _)
  private def X                         = BitPat("b?")
  private def N                         = BitPat("b0")
  private def Y                         = BitPat("b1")
  private def P_X                       = BitPat("b????")

  def C_X  = BitPat("b??")
  def C_RW = BitPat("b00")
  def C_RS = BitPat("b01")
  def C_RC = BitPat("b10")
  def SZ_C = C_X.getWidth

  def UOP_CSRRW  = cat(P_X, N, N, C_RW)
  def UOP_CSRRS  = cat(P_X, N, N, C_RS)
  def UOP_CSRRC  = cat(P_X, N, N, C_RC)
  def UOP_CSRRWI = cat(P_X, N, Y, C_RW)
  def UOP_CSRRSI = cat(P_X, N, Y, C_RS)
  def UOP_CSRRCI = cat(P_X, N, Y, C_RC)
  def UOP_MRET   = cat(P_X, Y, X, C_X)
}

trait Rv32iCsrFileMap {
  def CSR_CYCLE    = BitPat("b1100_0000_0000")
  def CSR_INSTRET  = BitPat("b1100_0000_0010")
  def CSR_CYCLEH   = BitPat("b1100_1000_0000")
  def CSR_INSTRETH = BitPat("b1100_1000_0010")

  def CSR_MSTATUS   = BitPat("b0011_0000_0000")
  def CSR_MISA      = BitPat("b0011_0000_0001")
  def CSR_MIE       = BitPat("b0011_0000_0100")
  def CSR_MTVEC     = BitPat("b0011_0000_0101")
  def CSR_MSCRATCH  = BitPat("b0011_0100_0000")
  def CSR_MEPC      = BitPat("b0011_0100_0001")
  def CSR_MCAUSE    = BitPat("b0011_0100_0010")
  def CSR_MIP       = BitPat("b0011_0100_0100")
  def CSR_MCYCLE    = BitPat("b1011_0000_0000")
  def CSR_MINSTRET  = BitPat("b1011_0000_0010")
  def CSR_MVENDORID = BitPat("b1111_0001_0001")
  def CSR_MARCHID   = BitPat("b1111_0001_0010")
  def CSR_MIMPID    = BitPat("b1111_0001_0011")
  def CSR_MHARTID   = BitPat("b1111_0001_0100")
  def CSR_MCYCLEH   = BitPat("b1011_1000_0000")
  def CSR_MINSTRETH = BitPat("b1011_1000_0010")

  def SZ_CSR = CSR_CYCLE.getWidth
}

object Rv32iCsrFile
    extends RegisteredNodeUtils[CsrFileImpl]
    with Rv32iCsrUopConsts
    with Rv32iCsrFileMap {
  override def utils: CsrFileImpl = new CsrFileImpl with Rv32iCsrUopConsts with Rv32iCsrFileMap {
    override def value: String  = "rv32i"
    override def addrWidth: Int = SZ_CSR
    override def opWidth: Int   = SZ_C

    override def table(implicit p: Parameters): Seq[(CsrRegister, CsrUpdateBehavior)] = Seq(
      (
        CsrRegister("cycle", CSR_CYCLE.value, 0x0L, writable = false),
        AlwaysUpdate(extra => extra("cycle")(31, 0))
      ),
      (
        CsrRegister("instret", CSR_INSTRET.value, 0x0L, writable = false),
        AlwaysUpdate(extra => extra("instret")(31, 0))
      ),
      (
        CsrRegister("cycleh", CSR_CYCLEH.value, 0x0L, writable = false),
        AlwaysUpdate(extra => extra("cycle")(63, 32))
      ),
      (
        CsrRegister("instreth", CSR_INSTRETH.value, 0x0L, writable = false),
        AlwaysUpdate(extra => extra("instret")(63, 32))
      ),
      (CsrRegister("mstatus", CSR_MSTATUS.value, 0x0L), NormalUpdate),
      (CsrRegister("misa", CSR_MISA.value, 0x40000100L, writable = false), NormalUpdate),
      (CsrRegister("mie", CSR_MIE.value, 0x0L), NormalUpdate),
      (CsrRegister("mtvec", CSR_MTVEC.value, 0x0L), NormalUpdate),
      (CsrRegister("mscratch", CSR_MSCRATCH.value, 0x0L), NormalUpdate),
      (CsrRegister("mepc", CSR_MEPC.value, 0x0L), NormalUpdate),
      (CsrRegister("mcause", CSR_MCAUSE.value, 0x0L), NormalUpdate),
      (
        CsrRegister("mip", CSR_MIP.value, 0x0L, writable = false),
        AlwaysUpdate { extra =>
          val meip = extra("ext_irq")
          val mtip = extra("timer_irq")
          val msip = extra("soft_irq")
          (meip << 11) | (mtip << 7) | (msip << 3)
        }
      ),
      (
        CsrRegister("mcycle", CSR_MCYCLE.value, 0x0L, writable = false),
        AlwaysUpdate(extra => extra("cycle")(31, 0))
      ),
      (
        CsrRegister("minstret", CSR_MINSTRET.value, 0x0L, writable = false),
        AlwaysUpdate(extra => extra("instret")(31, 0))
      ),
      (CsrRegister("mvendorid", CSR_MVENDORID.value, 0x0L, writable = false), NormalUpdate),
      (CsrRegister("marchid", CSR_MARCHID.value, 0x0L, writable = false), NormalUpdate),
      (CsrRegister("mimpid", CSR_MIMPID.value, 0x0L, writable = false), NormalUpdate),
      (CsrRegister("mhartid", CSR_MHARTID.value, 0x0L, writable = false), NormalUpdate),
      (
        CsrRegister("mcycleh", CSR_MCYCLEH.value, 0x0L, writable = false),
        AlwaysUpdate(extra => extra("cycle")(63, 32))
      ),
      (
        CsrRegister("minstreth", CSR_MINSTRETH.value, 0x0L, writable = false),
        AlwaysUpdate(extra => extra("instret")(63, 32))
      )
    )

    override def command(instr: UInt, uop: UInt, rs1: UInt, rd: UInt, rs1Data: UInt, imm: UInt)(
      implicit p: Parameters
    ): CsrFileCmd = {
      val cmd       = Wire(new CsrFileCmd(addrWidth, opWidth))
      val isSys     = uop(3)
      val isImm     = uop(2)
      val op        = uop(1, 0)
      val isRW      = op === C_RW.value.U(SZ_C.W)
      val isRS      = op === C_RS.value.U(SZ_C.W)
      val isRC      = op === C_RC.value.U(SZ_C.W)
      val src       = Mux(isImm, imm, rs1Data)
      val srcIsZero = Mux(isImm, imm === 0.U, rs1 === 0.U)

      cmd.valid := !isSys
      cmd.read  := !isSys && !(isRW && rd === 0.U)
      cmd.write := !isSys && (isRW || ((isRS || isRC) && !srcIsZero))
      cmd.addr  := instr(31, 20)
      cmd.op    := op
      cmd.data  := src

      cmd
    }

    override def write(old: UInt, cmd: CsrFileCmd)(implicit p: Parameters): UInt =
      MuxLookup(cmd.op, old)(
        Seq(
          C_RW.value.U(SZ_C.W) -> cmd.data,
          C_RS.value.U(SZ_C.W) -> (old | cmd.data),
          C_RC.value.U(SZ_C.W) -> (old & ~cmd.data)
        )
      )
  }

  override def registry: NodeDimensionRegistry[CsrFileImpl] =
    CsrFileFactory
}
