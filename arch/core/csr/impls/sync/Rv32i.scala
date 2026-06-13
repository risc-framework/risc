package arch.core.csr.impls.sync.rv32i

import arch.configs._
import arch.core.csr._
import arch.isa._
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.Cat

object CsrRv32iSync extends RegisteredNodeUtils[CsrSyncImpl] {
  override def utils: CsrSyncImpl = new CsrSyncImpl {
    override def value: String = "rv32i"

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

    private def get(regs: Map[String, UInt], name: String)(implicit p: Parameters): UInt =
      regs.getOrElse(name, 0.U(p(XLen).W))

    override def command(instr: UInt, uop: UInt)(implicit p: Parameters): CsrSyncCmd = {
      val cmd      = Wire(new CsrSyncCmd)
      val isMret   = isInstr(instr, "MRET")
      val isEcall  = isInstr(instr, "ECALL")
      val isEbreak = isInstr(instr, "EBREAK")

      cmd.trap_ret       := isMret
      cmd.sync_exception := isEcall || isEbreak
      cmd.cause          := Mux(isEbreak, 3.U(p(XLen).W), 11.U(p(XLen).W))

      cmd
    }

    override def illegalAccessCause(cmd: CsrFileCmd)(implicit p: Parameters): UInt =
      2.U(p(XLen).W)

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

    override def trapEntryUpdates(regs: Map[String, UInt], update: CsrTrapUpdate)(implicit
      p: Parameters
    ): Map[String, UInt] = {
      val mstatus    = get(regs, "mstatus")
      val mie        = mstatus(3)
      val newMstatus = Cat(mstatus(31, 8), mie, mstatus(6, 4), 0.U(1.W), mstatus(2, 0))

      Map(
        "mstatus" -> newMstatus,
        "mepc"    -> update.pc,
        "mcause"  -> update.cause
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

    override def trapTarget(view: CsrTrapView)(implicit p: Parameters): UInt =
      Cat(view.trapVector(p(XLen) - 1, 2), 0.U(2.W))
  }

  override def registry: NodeDimensionRegistry[CsrSyncImpl] =
    CsrSyncFactory
}
