package arch.core.exception.impls.isa.rv32i

import arch.core.exception._
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.BitPat

trait Rv32iExceptionKindConsts {
  def E_X  = BitPat("b????????")
  def SZ_E = E_X.getWidth

  def E_NONE                        = BitPat("b00000000")
  def E_REDIRECT                    = BitPat("b00000001")
  def E_INSTRUCTION_ADDR_MISALIGNED = BitPat("b00000010")
  def E_INSTRUCTION_ACCESS_FAULT    = BitPat("b00000011")
  def E_ILLEGAL_INSTRUCTION         = BitPat("b00000100")
  def E_BREAKPOINT                  = BitPat("b00000101")
  def E_LOAD_ADDR_MISALIGNED        = BitPat("b00000110")
  def E_LOAD_ACCESS_FAULT           = BitPat("b00000111")
  def E_STORE_ADDR_MISALIGNED       = BitPat("b00001000")
  def E_STORE_ACCESS_FAULT          = BitPat("b00001001")
  def E_ECALL_U                     = BitPat("b00001010")
  def E_ECALL_S                     = BitPat("b00001011")
  def E_ECALL_M                     = BitPat("b00001100")
  def E_INSTRUCTION_PAGE_FAULT      = BitPat("b00001101")
  def E_LOAD_PAGE_FAULT             = BitPat("b00001110")
  def E_STORE_PAGE_FAULT            = BitPat("b00001111")
  def E_TRAP_RETURN                 = BitPat("b00010000")
  def E_MACHINE_SOFTWARE_INTERRUPT  = BitPat("b00100000")
  def E_MACHINE_TIMER_INTERRUPT     = BitPat("b00100001")
  def E_MACHINE_EXTERNAL_INTERRUPT  = BitPat("b00100010")
  def E_CUSTOM                      = BitPat("b11111111")

  def E(kind: BitPat): UInt = kind.value.U(SZ_E.W)
}

trait Rv32iExceptionCauseConsts {
  def CAUSE_INSTRUCTION_ADDR_MISALIGNED = 0
  def CAUSE_INSTRUCTION_ACCESS_FAULT    = 1
  def CAUSE_ILLEGAL_INSTRUCTION         = 2
  def CAUSE_BREAKPOINT                  = 3
  def CAUSE_LOAD_ADDR_MISALIGNED        = 4
  def CAUSE_LOAD_ACCESS_FAULT           = 5
  def CAUSE_STORE_ADDR_MISALIGNED       = 6
  def CAUSE_STORE_ACCESS_FAULT          = 7
  def CAUSE_ECALL_U                     = 8
  def CAUSE_ECALL_S                     = 9
  def CAUSE_ECALL_M                     = 11
  def CAUSE_INSTRUCTION_PAGE_FAULT      = 12
  def CAUSE_LOAD_PAGE_FAULT             = 13
  def CAUSE_STORE_PAGE_FAULT            = 15

  def CAUSE_MACHINE_SOFTWARE_INTERRUPT = 3
  def CAUSE_MACHINE_TIMER_INTERRUPT    = 7
  def CAUSE_MACHINE_EXTERNAL_INTERRUPT = 11
}

object ExceptionRv32iIsa
    extends RegisteredNodeUtils[ExceptionIsaImpl]
    with Rv32iExceptionKindConsts
    with Rv32iExceptionCauseConsts {
  override def utils: ExceptionIsaImpl = new ExceptionIsaImpl
    with Rv32iExceptionKindConsts
    with Rv32iExceptionCauseConsts {
    override def value: String = "rv32i"

    override def kindWidth: Int  = SZ_E
    override def causeWidth: Int = 32

    private object RedirectEntry extends ExceptionRedirectEntry {
      override def kind: BitPat  = E_REDIRECT
      override def priority: Int = 16
    }

    private object InstructionAddrMisalignedEntry extends ExceptionSyncEntry {
      override def kind: BitPat             = E_INSTRUCTION_ADDR_MISALIGNED
      override def cause: BigInt            = CAUSE_INSTRUCTION_ADDR_MISALIGNED
      override def priority: Int            = 0
      override def writeCsr: Boolean        = true
      override def isRet: Boolean           = false
      override def requiresCsrIdle: Boolean = true
    }

    private object InstructionAccessFaultEntry extends ExceptionSyncEntry {
      override def kind: BitPat             = E_INSTRUCTION_ACCESS_FAULT
      override def cause: BigInt            = CAUSE_INSTRUCTION_ACCESS_FAULT
      override def priority: Int            = 0
      override def writeCsr: Boolean        = true
      override def isRet: Boolean           = false
      override def requiresCsrIdle: Boolean = true
    }

    private object IllegalInstructionEntry extends ExceptionSyncEntry {
      override def kind: BitPat             = E_ILLEGAL_INSTRUCTION
      override def cause: BigInt            = CAUSE_ILLEGAL_INSTRUCTION
      override def priority: Int            = 0
      override def writeCsr: Boolean        = true
      override def isRet: Boolean           = false
      override def requiresCsrIdle: Boolean = true
    }

    private object BreakpointEntry extends ExceptionSyncEntry {
      override def kind: BitPat             = E_BREAKPOINT
      override def cause: BigInt            = CAUSE_BREAKPOINT
      override def priority: Int            = 0
      override def writeCsr: Boolean        = true
      override def isRet: Boolean           = false
      override def requiresCsrIdle: Boolean = true
    }

    private object LoadAddrMisalignedEntry extends ExceptionSyncEntry {
      override def kind: BitPat             = E_LOAD_ADDR_MISALIGNED
      override def cause: BigInt            = CAUSE_LOAD_ADDR_MISALIGNED
      override def priority: Int            = 0
      override def writeCsr: Boolean        = true
      override def isRet: Boolean           = false
      override def requiresCsrIdle: Boolean = true
    }

    private object LoadAccessFaultEntry extends ExceptionSyncEntry {
      override def kind: BitPat             = E_LOAD_ACCESS_FAULT
      override def cause: BigInt            = CAUSE_LOAD_ACCESS_FAULT
      override def priority: Int            = 0
      override def writeCsr: Boolean        = true
      override def isRet: Boolean           = false
      override def requiresCsrIdle: Boolean = true
    }

    private object StoreAddrMisalignedEntry extends ExceptionSyncEntry {
      override def kind: BitPat             = E_STORE_ADDR_MISALIGNED
      override def cause: BigInt            = CAUSE_STORE_ADDR_MISALIGNED
      override def priority: Int            = 0
      override def writeCsr: Boolean        = true
      override def isRet: Boolean           = false
      override def requiresCsrIdle: Boolean = true
    }

    private object StoreAccessFaultEntry extends ExceptionSyncEntry {
      override def kind: BitPat             = E_STORE_ACCESS_FAULT
      override def cause: BigInt            = CAUSE_STORE_ACCESS_FAULT
      override def priority: Int            = 0
      override def writeCsr: Boolean        = true
      override def isRet: Boolean           = false
      override def requiresCsrIdle: Boolean = true
    }

    private object EcallUEntry extends ExceptionSyncEntry {
      override def kind: BitPat             = E_ECALL_U
      override def cause: BigInt            = CAUSE_ECALL_U
      override def priority: Int            = 0
      override def writeCsr: Boolean        = true
      override def isRet: Boolean           = false
      override def requiresCsrIdle: Boolean = true
    }

    private object EcallSEntry extends ExceptionSyncEntry {
      override def kind: BitPat             = E_ECALL_S
      override def cause: BigInt            = CAUSE_ECALL_S
      override def priority: Int            = 0
      override def writeCsr: Boolean        = true
      override def isRet: Boolean           = false
      override def requiresCsrIdle: Boolean = true
    }

    private object EcallMEntry extends ExceptionSyncEntry {
      override def kind: BitPat             = E_ECALL_M
      override def cause: BigInt            = CAUSE_ECALL_M
      override def priority: Int            = 0
      override def writeCsr: Boolean        = true
      override def isRet: Boolean           = false
      override def requiresCsrIdle: Boolean = true
    }

    private object InstructionPageFaultEntry extends ExceptionSyncEntry {
      override def kind: BitPat             = E_INSTRUCTION_PAGE_FAULT
      override def cause: BigInt            = CAUSE_INSTRUCTION_PAGE_FAULT
      override def priority: Int            = 0
      override def writeCsr: Boolean        = true
      override def isRet: Boolean           = false
      override def requiresCsrIdle: Boolean = true
    }

    private object LoadPageFaultEntry extends ExceptionSyncEntry {
      override def kind: BitPat             = E_LOAD_PAGE_FAULT
      override def cause: BigInt            = CAUSE_LOAD_PAGE_FAULT
      override def priority: Int            = 0
      override def writeCsr: Boolean        = true
      override def isRet: Boolean           = false
      override def requiresCsrIdle: Boolean = true
    }

    private object StorePageFaultEntry extends ExceptionSyncEntry {
      override def kind: BitPat             = E_STORE_PAGE_FAULT
      override def cause: BigInt            = CAUSE_STORE_PAGE_FAULT
      override def priority: Int            = 0
      override def writeCsr: Boolean        = true
      override def isRet: Boolean           = false
      override def requiresCsrIdle: Boolean = true
    }

    private object TrapReturnEntry extends ExceptionSyncEntry {
      override def kind: BitPat             = E_TRAP_RETURN
      override def cause: BigInt            = 0
      override def priority: Int            = 0
      override def writeCsr: Boolean        = true
      override def isRet: Boolean           = true
      override def requiresCsrIdle: Boolean = true
    }

    private object MachineSoftwareInterruptEntry extends ExceptionAsyncEntry {
      override def kind: BitPat             = E_MACHINE_SOFTWARE_INTERRUPT
      override def cause: BigInt            = CAUSE_MACHINE_SOFTWARE_INTERRUPT
      override def priority: Int            = 64
      override def writeCsr: Boolean        = true
      override def requiresCsrIdle: Boolean = true
    }

    private object MachineTimerInterruptEntry extends ExceptionAsyncEntry {
      override def kind: BitPat             = E_MACHINE_TIMER_INTERRUPT
      override def cause: BigInt            = CAUSE_MACHINE_TIMER_INTERRUPT
      override def priority: Int            = 64
      override def writeCsr: Boolean        = true
      override def requiresCsrIdle: Boolean = true
    }

    private object MachineExternalInterruptEntry extends ExceptionAsyncEntry {
      override def kind: BitPat             = E_MACHINE_EXTERNAL_INTERRUPT
      override def cause: BigInt            = CAUSE_MACHINE_EXTERNAL_INTERRUPT
      override def priority: Int            = 64
      override def writeCsr: Boolean        = true
      override def requiresCsrIdle: Boolean = true
    }

    override def redirectEntries: Seq[ExceptionRedirectEntry] = Seq(
      RedirectEntry
    )

    override def syncEntries: Seq[ExceptionSyncEntry] = Seq(
      InstructionAddrMisalignedEntry,
      InstructionAccessFaultEntry,
      IllegalInstructionEntry,
      BreakpointEntry,
      LoadAddrMisalignedEntry,
      LoadAccessFaultEntry,
      StoreAddrMisalignedEntry,
      StoreAccessFaultEntry,
      EcallUEntry,
      EcallSEntry,
      EcallMEntry,
      InstructionPageFaultEntry,
      LoadPageFaultEntry,
      StorePageFaultEntry,
      TrapReturnEntry
    )

    override def asyncEntries: Seq[ExceptionAsyncEntry] = Seq(
      MachineExternalInterruptEntry,
      MachineSoftwareInterruptEntry,
      MachineTimerInterruptEntry
    )
  }

  override def registry: NodeDimensionRegistry[ExceptionIsaImpl] =
    ExceptionIsaFactory
}
