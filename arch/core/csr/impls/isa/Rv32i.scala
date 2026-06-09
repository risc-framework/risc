package arch.core.csr.impls.isa.rv32i

import arch.configs._
import arch.core.csr._
import arch.isa._
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.{ BitPat, Cat, MuxLookup }

trait Rv32iCsrUOpConsts {
  private def cat(bps: BitPat*): BitPat = bps.reduce(_ ## _)
  private def X                         = BitPat("b?")
  private def N                         = BitPat("b0")
  private def Y                         = BitPat("b1")
  private def P_X                       = BitPat("b????")

  def C_X  = BitPat("b??")
  def SZ_C = C_X.getWidth
  def C_RW = BitPat("b00")
  def C_RS = BitPat("b01")
  def C_RC = BitPat("b10")

  def UOP_CSRRW  = cat(P_X, N, N, C_RW)
  def UOP_CSRRS  = cat(P_X, N, N, C_RS)
  def UOP_CSRRC  = cat(P_X, N, N, C_RC)
  def UOP_CSRRWI = cat(P_X, N, Y, C_RW)
  def UOP_CSRRSI = cat(P_X, N, Y, C_RS)
  def UOP_CSRRCI = cat(P_X, N, Y, C_RC)
  def UOP_MRET   = cat(P_X, Y, X, C_X)
}

trait Rv32iCsrMap {
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

object CsrRv32iIsa extends RegisteredNodeUtils[CsrIsaImpl] with Rv32iCsrUOpConsts with Rv32iCsrMap {
  override def utils: CsrIsaImpl = new CsrIsaImpl with Rv32iCsrUOpConsts with Rv32iCsrMap {
    private def enc(name: String): InstructionEncoding =
      IsaFactory
        .instrSet(value)
        .all
        .find(_.name == name)
        .getOrElse {
          throw new NoSuchElementException(s"Instruction '$name' not found in ISA '$value'")
        }

    private def isInstr(instr: UInt, name: String)(implicit p: Parameters): Bool = {
      val e = enc(name)
      (instr & e.mask.U(p(ILen).W)) === e.value.U(p(ILen).W)
    }

    override def value: String  = "rv32i"
    override def addrWidth: Int = SZ_CSR
    override def opWidth: Int   = SZ_C

    override def getAddr(instr: UInt)(implicit p: Parameters): UInt =
      instr(31, 20)

    override def decode(uop: UInt): CsrCtrl = {
      val ctrl = Wire(new CsrCtrl(opWidth))
      ctrl.is_sys := uop(3)
      ctrl.is_imm := uop(2)
      ctrl.op     := uop(1, 0)
      ctrl
    }

    override def fn(op: UInt, csrData: UInt, srcData: UInt)(implicit p: Parameters): UInt =
      MuxLookup(op, 0.U(p(XLen).W))(
        Seq(
          C_RW.value.U(SZ_C.W) -> srcData,
          C_RS.value.U(SZ_C.W) -> (csrData | srcData),
          C_RC.value.U(SZ_C.W) -> (csrData & ~srcData)
        )
      )

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

    private def get(regs: Map[String, UInt], name: String)(implicit p: Parameters): UInt =
      regs.getOrElse(name, 0.U(p(XLen).W))

    override def view(regs: Map[String, UInt], extra: Map[String, UInt])(implicit
      p: Parameters
    ): CsrTrapView = {
      val v = Wire(new CsrTrapView)
      v.status           := get(regs, "mstatus")
      v.interruptEnable  := get(regs, "mie")
      v.interruptPending := get(regs, "mip")
      v.trapVector       := get(regs, "mtvec")
      v.epc              := get(regs, "mepc")
      v
    }

    override def trapEntryUpdates(
      regs: Map[String, UInt],
      pc: UInt,
      cause: UInt
    )(implicit p: Parameters): Map[String, UInt] = {
      val mstatus    = get(regs, "mstatus")
      val mie        = mstatus(3)
      val newMstatus = Cat(mstatus(31, 8), mie, mstatus(6, 4), 0.U(1.W), mstatus(2, 0))

      Map(
        "mstatus" -> newMstatus,
        "mepc"    -> pc,
        "mcause"  -> cause
      )
    }

    override def trapReturnTarget(regs: Map[String, UInt])(implicit p: Parameters): UInt =
      get(regs, "mepc")

    override def trapReturnUpdates(
      regs: Map[String, UInt]
    )(implicit p: Parameters): Map[String, UInt] = {
      val mstatus    = get(regs, "mstatus")
      val mpie       = mstatus(7)
      val newMstatus = Cat(mstatus(31, 8), 1.U(1.W), mstatus(6, 4), mpie, mstatus(2, 0))

      Map("mstatus" -> newMstatus)
    }

    override def isTrapReturn(instr: UInt, uop: UInt)(implicit p: Parameters): Bool =
      isInstr(instr, "MRET")

    override def hasSyncException(instr: UInt, uop: UInt)(implicit p: Parameters): Bool =
      isInstr(instr, "ECALL") || isInstr(instr, "EBREAK")

    override def syncExceptionCause(instr: UInt, uop: UInt)(implicit p: Parameters): UInt = {
      val isEbreak = isInstr(instr, "EBREAK")
      Mux(isEbreak, 3.U(p(XLen).W), 11.U(p(XLen).W))
    }
  }

  override def registry: NodeDimensionRegistry[CsrIsaImpl] =
    CsrIsaFactory
}
