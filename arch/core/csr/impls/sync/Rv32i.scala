package arch.core.csr.impls.sync.rv32i

import arch.configs._
import arch.core.csr._
import arch.core.exception.ExceptionTrapUpdate
import arch.core.exception.impls.isa.rv32i.Rv32iExceptionKindConsts
import arch.isa.Rv32i
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.Cat

object Rv32iCsrSync extends RegisteredNodeUtils[CsrSyncImpl] with Rv32iExceptionKindConsts {
  override def utils: CsrSyncImpl = new CsrSyncImpl with Rv32iExceptionKindConsts {
    override def value: String = "rv32i"

    override def command(instr: UInt, uop: UInt, view: CsrTrapView)(implicit
      p: Parameters
    ): CsrSysCmd = {
      val cmd      = WireDefault(0.U.asTypeOf(new CsrSysCmd))
      val isSys    = uop(3)
      val isEcall  = isSys && instr === Rv32i.isa.bitPat("ECALL").value.U(p(ILen).W)
      val isEbreak = isSys && instr === Rv32i.isa.bitPat("EBREAK").value.U(p(ILen).W)
      val isMret   = isSys && instr === Rv32i.isa.bitPat("MRET").value.U(p(ILen).W)
      val trapVec  = Cat(view.trapVector(p(XLen) - 1, 2), 0.U(2.W))

      cmd.valid  := isEcall || isEbreak || isMret
      cmd.kind   := Mux(isMret, E(E_TRAP_RETURN), Mux(isEbreak, E(E_BREAKPOINT), E(E_ECALL_M)))
      cmd.target := Mux(isMret, view.epc, trapVec)

      cmd
    }

    override def illegalAccessKind(cmd: CsrFileCmd)(implicit p: Parameters): UInt =
      E(E_ILLEGAL_INSTRUCTION)

    override def view(regs: Map[String, UInt], extra: Map[String, UInt])(implicit
      p: Parameters
    ): CsrTrapView = {
      val out = Wire(new CsrTrapView)

      out.status           := regs.getOrElse("mstatus", 0.U(p(XLen).W))
      out.interruptEnable  := regs.getOrElse("mie", 0.U(p(XLen).W))
      out.interruptPending := regs.getOrElse("mip", 0.U(p(XLen).W))
      out.trapVector       := regs.getOrElse("mtvec", 0.U(p(XLen).W))
      out.epc              := regs.getOrElse("mepc", 0.U(p(XLen).W))

      out
    }

    override def trapUpdates(regs: Map[String, UInt], update: ExceptionTrapUpdate)(implicit
      p: Parameters
    ): Map[String, UInt] = {
      val oldMstatus = regs.getOrElse("mstatus", 0.U(p(XLen).W))
      val oldMepc    = regs.getOrElse("mepc", 0.U(p(XLen).W))
      val oldMcause  = regs.getOrElse("mcause", 0.U(p(XLen).W))

      val mie               = oldMstatus(3)
      val clearEntryMie     = oldMstatus & ~(BigInt(1) << 3).U(p(XLen).W)
      val clearEntryMpieMpp = clearEntryMie & ~((BigInt(1) << 7) | (BigInt(3) << 11)).U(p(XLen).W)
      val entryMstatus      = clearEntryMpieMpp | (mie.asUInt << 7) | (3.U(p(XLen).W) << 11)

      val mpie          = oldMstatus(7)
      val clearReturn   =
        oldMstatus & ~((BigInt(1) << 3) | (BigInt(1) << 7) | (BigInt(3) << 11)).U(p(XLen).W)
      val returnMstatus = clearReturn | (mpie.asUInt << 3) | (1.U(p(XLen).W) << 7)

      Map(
        "mstatus" -> Mux(update.is_ret, returnMstatus, entryMstatus),
        "mepc"    -> Mux(update.is_ret, oldMepc, update.pc),
        "mcause"  -> Mux(update.is_ret, oldMcause, update.cause.asUInt.pad(p(XLen)))
      )
    }

    override def trapTarget(view: CsrTrapView)(implicit p: Parameters): UInt =
      Cat(view.trapVector(p(XLen) - 1, 2), 0.U(2.W))
  }

  override def registry: NodeDimensionRegistry[CsrSyncImpl] =
    CsrSyncFactory
}
