package arch.core.csr.riscv

import arch.core.csr._
import arch.core.regfile.Register
import arch.configs._
import chisel3._
import chisel3.util.{ BitPat, MuxLookup, Cat }

// Format: uop[7:4] = 0 | uop[3] = is_sys | uop[2] = is_imm | uop[1:0] = op
trait RV32ICsrUOpConsts {
  private def cat(bps: BitPat*): BitPat = bps.reduce(_ ## _)
  private def X                         = BitPat("b?")
  private def N                         = BitPat("b0")
  private def Y                         = BitPat("b1")
  private def P_X                       = BitPat("b????")

  def C_X  = BitPat("b??")
  def SZ_C = C_X.getWidth
  def C_RW = BitPat("b00") // Write
  def C_RS = BitPat("b01") // Set
  def C_RC = BitPat("b10") // Clear

  def UOP_CSRRW  = cat(P_X, N, N, C_RW)
  def UOP_CSRRS  = cat(P_X, N, N, C_RS)
  def UOP_CSRRC  = cat(P_X, N, N, C_RC)
  def UOP_CSRRWI = cat(P_X, N, Y, C_RW)
  def UOP_CSRRSI = cat(P_X, N, Y, C_RS)
  def UOP_CSRRCI = cat(P_X, N, Y, C_RC)

  def UOP_MRET = cat(P_X, Y, X, C_X)
}

trait RV32ICsrMap {
  def CSR_U  = BitPat("b??00_????_????")
  def CSR_S  = BitPat("b??01_????_????")
  def CSR_H  = BitPat("b??10_????_????")
  def CSR_M  = BitPat("b??11_????_????")
  def SZ_CSR = CSR_U.getWidth

  // U-mode
  def CSR_CYCLE    = BitPat("b1100_0000_0000")
  def CSR_INSTRET  = BitPat("b1100_0000_0010")
  def CSR_CYCLEH   = BitPat("b1100_1000_0000")
  def CSR_INSTRETH = BitPat("b1100_1000_0010")

  // M-mode
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
  def CSR_MVENDERID = BitPat("b1111_0001_0001")
  def CSR_MARCHID   = BitPat("b1111_0001_0010")
  def CSR_MIMPID    = BitPat("b1111_0001_0011")
  def CSR_MHARTID   = BitPat("b1111_0001_0100")
  def CSR_MCYCLEH   = BitPat("b1011_1000_0000")
  def CSR_MINSTRETH = BitPat("b1011_1000_0010")
}

object RV32ICsrUtils extends RegisteredUtils[CsrUtils] with RV32ICsrUOpConsts with RV32ICsrMap {
  override def utils: CsrUtils = new CsrUtils {
    override def name: String = "rv32i"

    override def addrWidth: Int = SZ_CSR
    override def opWidth: Int   = SZ_C

    override def genImm(instr: UInt): UInt = {
      val zimm = instr(19, 15)
      Cat(0.U((p(XLen) - 5).W), zimm)
    }

    override def getAddr(instr: UInt): UInt = instr(31, 20)

    override def decode(uop: UInt): CsrCtrl = {
      val ctrl = Wire(new CsrCtrl(opWidth))
      ctrl.is_sys := uop(3)
      ctrl.is_imm := uop(2)
      ctrl.op     := uop(1, 0)
      ctrl
    }

    override def fn(op: UInt, csr_data: UInt, src_data: UInt): UInt =
      MuxLookup(op, 0.U(p(XLen).W))(
        Seq(
          C_RW.value.U(SZ_C.W) -> src_data,
          C_RS.value.U(SZ_C.W) -> (csr_data | src_data),
          C_RC.value.U(SZ_C.W) -> (csr_data & ~src_data),
        )
      )

    override def extraInputs: Seq[(String, Int)] = Seq(
      "cycle"     -> 64,
      "instret"   -> 64,
      "timer_irq" -> 1,
      "soft_irq"  -> 1,
      "ext_irq"   -> 1
    )

    override def table: Seq[(Register, CsrUpdateBehavior)] = Seq(
      // U-mode
      (
        Register("cycle", CSR_CYCLE.value, 0x0L, writable = false),
        AlwaysUpdate(params => params("cycle")(31, 0))
      ),
      (
        Register("instret", CSR_INSTRET.value, 0x0L, writable = false),
        AlwaysUpdate(params => params("instret")(31, 0))
      ),
      (
        Register("cycleh", CSR_CYCLEH.value, 0x0L, writable = false),
        AlwaysUpdate(params => params("cycle")(63, 32))
      ),
      (
        Register("instreth", CSR_INSTRETH.value, 0x0L, writable = false),
        AlwaysUpdate(params => params("instret")(63, 32))
      ),

      // M-mode
      (Register("mstatus", CSR_MSTATUS.value, 0x0L), NormalUpdate),
      (Register("misa", CSR_MISA.value, 0x40000100L, writable = false), NormalUpdate),
      (Register("mie", CSR_MIE.value, 0x0L), NormalUpdate),
      (Register("mtvec", CSR_MTVEC.value, 0x0L), NormalUpdate),
      (Register("mscratch", CSR_MSCRATCH.value, 0x0L), NormalUpdate),
      (Register("mepc", CSR_MEPC.value, 0x0L), NormalUpdate),
      (Register("mcause", CSR_MCAUSE.value, 0x0L), NormalUpdate),
      (
        Register("mip", CSR_MIP.value, 0x0L, writable = false),
        AlwaysUpdate { params =>
          val meip = params("ext_irq")   // bit 11
          val mtip = params("timer_irq") // bit 7
          val msip = params("soft_irq")  // bit 3
          (meip << 11) | (mtip << 7) | (msip << 3)
        }
      ),
      (
        Register("mcycle", CSR_MCYCLE.value, 0x0L, writable = false),
        AlwaysUpdate(params => params("cycle")(31, 0))
      ),
      (
        Register("minstret", CSR_MINSTRET.value, 0x0L, writable = false),
        AlwaysUpdate(params => params("instret")(31, 0))
      ),
      (Register("mvendorid", CSR_MVENDERID.value, 0x0L, writable = false), NormalUpdate),
      (Register("marchid", CSR_MARCHID.value, 0x0L, writable = false), NormalUpdate),
      (Register("mimpid", CSR_MIMPID.value, 0x0L, writable = false), NormalUpdate),
      (Register("mhartid", CSR_MHARTID.value, 0x0L, writable = false), NormalUpdate),
      (
        Register("mcycleh", CSR_MCYCLEH.value, 0x0L, writable = false),
        AlwaysUpdate(params => params("cycle")(63, 32))
      ),
      (
        Register("minstreth", CSR_MINSTRETH.value, 0x0L, writable = false),
        AlwaysUpdate(params => params("instret")(63, 32))
      ),
    )

    override def checkInterrupts(
      regs: Map[String, UInt],
      extra: Map[String, UInt]
    ): (Bool, UInt, UInt) = {
      val mstatus = regs.getOrElse("mstatus", 0.U)
      val mie     = regs.getOrElse("mie", 0.U)
      val mip     = regs.getOrElse("mip", 0.U)
      val mtvec   = regs.getOrElse("mtvec", 0.U)

      val mstatus_mie         = mstatus(3)
      val pending_and_enabled = mip & mie

      val ext_irq = pending_and_enabled(11) // MEIP
      val tim_irq = pending_and_enabled(7)  // MTIP
      val sft_irq = pending_and_enabled(3)  // MSIP

      val take_irq = mstatus_mie && (ext_irq || sft_irq || tim_irq)

      val async_bit = 1.U(1.W) << 31
      val cause     = Mux(
        ext_irq,
        async_bit | 11.U,
        Mux(sft_irq, async_bit | 3.U, Mux(tim_irq, async_bit | 7.U, 0.U))
      )

      val target = mtvec

      (take_irq, target, cause)
    }

    override def getTrapUpdates(
      regs: Map[String, UInt],
      pc: UInt,
      cause: UInt
    ): Map[String, UInt] = {
      val mstatus = regs.getOrElse("mstatus", 0.U)

      val mie_bit     = mstatus(3)
      val new_mstatus = Cat(mstatus(31, 8), mie_bit, mstatus(6, 4), 0.U(1.W), mstatus(2, 0))

      Map(
        "mstatus" -> new_mstatus,
        "mepc"    -> pc,
        "mcause"  -> cause
      )
    }

    override def getTrapReturnTarget(regs: Map[String, UInt]): UInt =
      regs.getOrElse("mepc", 0.U)

    override def getTrapReturnUpdates(regs: Map[String, UInt]): Map[String, UInt] = {
      val mstatus = regs.getOrElse("mstatus", 0.U)

      val mpie        = mstatus(7)
      val new_mstatus = Cat(mstatus(31, 8), 1.U(1.W), mstatus(6, 4), mpie, mstatus(2, 0))

      Map(
        "mstatus" -> new_mstatus
      )
    }
  }

  override def factory: UtilsFactory[CsrUtils] = CsrUtilsFactory
}
