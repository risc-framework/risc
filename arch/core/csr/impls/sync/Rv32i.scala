package arch.core.csr.impls.sync.rv32i

import arch.configs._
import arch.core.csr._
import arch.core.exception.ExceptionTrapUpdate
import arch.core.exception.impls.isa.rv32i.Rv32iExceptionKindConsts
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.Cat

object Rv32iCsrSync extends RegisteredNodeUtils[CsrSyncImpl] with Rv32iExceptionKindConsts {
  override def utils: CsrSyncImpl = new CsrSyncImpl with Rv32iExceptionKindConsts {
    override def value: String = "rv32i"

    override def command(instr: UInt, uop: UInt)(implicit p: Parameters): CsrSyncCmd = {
      val cmd      = Wire(new CsrSyncCmd)
      val isSys    = uop(3)
      val isEcall  = isSys && instr === "h00000073".U(p(ILen).W)
      val isEbreak = isSys && instr === "h00100073".U(p(ILen).W)
      val isMret   = isSys && instr === "h30200073".U(p(ILen).W)

      cmd.trap_ret       := isMret
      cmd.sync_exception := isEcall || isEbreak
      cmd.kind           := Mux(isMret, E(E_TRAP_RETURN), Mux(isEbreak, E(E_BREAKPOINT), E(E_ECALL_M)))

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

    override def trapEntryUpdates(regs: Map[String, UInt], update: ExceptionTrapUpdate)(implicit
      p: Parameters
    ): Map[String, UInt] = {
      val mstatus      = regs.getOrElse("mstatus", 0.U(p(XLen).W))
      val mie          = mstatus(3)
      val clearMie     = mstatus & ~(BigInt(1) << 3).U(p(XLen).W)
      val clearMpieMpp = clearMie & ~((BigInt(1) << 7) | (BigInt(3) << 11)).U(p(XLen).W)
      val nextMstatus  = clearMpieMpp | (mie.asUInt << 7) | (3.U(p(XLen).W) << 11)

      Map(
        "mstatus" -> nextMstatus,
        "mepc"    -> update.pc,
        "mcause"  -> update.cause.asUInt.pad(p(XLen))
      )
    }

    override def trapReturnTarget(regs: Map[String, UInt])(implicit p: Parameters): UInt =
      regs.getOrElse("mepc", 0.U(p(XLen).W))

    override def trapReturnUpdates(
      regs: Map[String, UInt]
    )(implicit p: Parameters): Map[String, UInt] = {
      val mstatus = regs.getOrElse("mstatus", 0.U(p(XLen).W))
      val mpie    = mstatus(7)
      val clear   = mstatus & ~((BigInt(1) << 3) | (BigInt(1) << 7) | (BigInt(3) << 11)).U(p(XLen).W)
      val next    = clear | (mpie.asUInt << 3) | (1.U(p(XLen).W) << 7)

      Map("mstatus" -> next)
    }

    override def trapTarget(view: CsrTrapView)(implicit p: Parameters): UInt =
      Cat(view.trapVector(p(XLen) - 1, 2), 0.U(2.W))
  }

  override def registry: NodeDimensionRegistry[CsrSyncImpl] =
    CsrSyncFactory
}
