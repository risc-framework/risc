package arch.core.ld

import arch.configs._
import chisel3._
import chisel3.util.{ Cat, Fill, MuxCase }

class LoadCtrl(implicit p: Parameters) extends Bundle {
  val is_byte     = Bool()
  val is_half     = Bool()
  val is_word     = Bool()
  val is_dword    = Bool()
  val is_unsigned = Bool()
  val strb        = UInt(p(BytesPerWord).W)
}

class LdDebugInfo extends Bundle {
  val busy         = Bool()
  val wait_mem     = Bool()
  val wait_forward = Bool()
}

trait LoadDataHelpers {
  def alignedAddr(addr: UInt)(implicit p: Parameters): UInt =
    Cat(addr(p(XLen) - 1, p(BytesOffsetWidth)), 0.U(p(BytesOffsetWidth).W))

  def byteOffset(addr: UInt)(implicit p: Parameters): UInt =
    addr(p(BytesOffsetWidth) - 1, 0)

  private def lowByteMask(bytes: Int)(implicit p: Parameters): UInt =
    ((BigInt(1) << bytes) - 1).U(p(BytesPerWord).W)

  def expandByteMask(mask: UInt)(implicit p: Parameters): UInt =
    Cat((p(BytesPerWord) - 1 to 0 by -1).map(i => Fill(8, mask(i))))

  def rawLoadMask(ctrl: LoadCtrl)(implicit p: Parameters): UInt =
    MuxCase(
      Fill(p(BytesPerWord), 1.U(1.W)).asUInt,
      Seq(
        ctrl.is_byte  -> lowByteMask(1),
        ctrl.is_half  -> lowByteMask(2),
        ctrl.is_word  -> lowByteMask(4),
        ctrl.is_dword -> Fill(p(BytesPerWord), 1.U(1.W)).asUInt
      )
    )

  def shiftedLoadMask(ctrl: LoadCtrl, addr: UInt)(implicit p: Parameters): UInt =
    (rawLoadMask(ctrl) << byteOffset(addr))(p(BytesPerWord) - 1, 0)

  def loadResult(ctrl: LoadCtrl, addr: UInt, alignedData: UInt)(implicit p: Parameters): UInt = {
    val shifted = alignedData >> (byteOffset(addr) << 3)

    MuxCase(
      shifted,
      Seq(
        ctrl.is_byte  -> Cat(Fill(p(XLen) - 8, !ctrl.is_unsigned && shifted(7)), shifted(7, 0)),
        ctrl.is_half  -> Cat(Fill(p(XLen) - 16, !ctrl.is_unsigned && shifted(15)), shifted(15, 0)),
        ctrl.is_word  -> {
          if (p(XLen) == 64)
            Cat(Fill(p(XLen) - 32, !ctrl.is_unsigned && shifted(31)), shifted(31, 0))
          else shifted
        },
        ctrl.is_dword -> shifted
      )
    )
  }
}
