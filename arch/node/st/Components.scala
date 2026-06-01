package arch.node.st

import arch.node.fupool.FuIO
import arch.node.sb.StoreWriteBundle
import arch.configs._
import chisel3._
import chisel3.util.{ Cat, Fill, MuxCase, Valid }

class StoreCtrl(implicit p: Parameters) extends Bundle {
  val is_byte  = Bool()
  val is_half  = Bool()
  val is_word  = Bool()
  val is_dword = Bool()
  val strb     = UInt(p(BytesPerWord).W)
}

class StSbWriteIO(implicit p: Parameters) extends Bundle {
  val write = Valid(new StoreWriteBundle)
}

class StIO(implicit p: Parameters) extends Bundle {
  val fu = new FuIO
  val sb = new StSbWriteIO
}

trait StoreDataHelpers {
  def alignedAddr(addr: UInt)(implicit p: Parameters): UInt =
    Cat(addr(p(XLen) - 1, p(BytesOffsetWidth)), 0.U(p(BytesOffsetWidth).W))

  def byteOffset(addr: UInt)(implicit p: Parameters): UInt =
    addr(p(BytesOffsetWidth) - 1, 0)

  private def lowByteMask(bytes: Int)(implicit p: Parameters): UInt =
    ((BigInt(1) << bytes) - 1).U(p(BytesPerWord).W)

  def rawStoreMask(ctrl: StoreCtrl)(implicit p: Parameters): UInt =
    MuxCase(
      Fill(p(BytesPerWord), 1.U(1.W)).asUInt,
      Seq(
        ctrl.is_byte  -> lowByteMask(1),
        ctrl.is_half  -> lowByteMask(2),
        ctrl.is_word  -> lowByteMask(4),
        ctrl.is_dword -> Fill(p(BytesPerWord), 1.U(1.W)).asUInt
      )
    )

  def shiftedStoreMask(ctrl: StoreCtrl, addr: UInt)(implicit p: Parameters): UInt =
    (rawStoreMask(ctrl) << byteOffset(addr))(p(BytesPerWord) - 1, 0)

  def narrowStoreData(ctrl: StoreCtrl, data: UInt)(implicit p: Parameters): UInt =
    MuxCase(
      data,
      Seq(
        ctrl.is_byte  -> Cat(Fill(p(XLen) - 8, 0.U), data(7, 0)),
        ctrl.is_half  -> Cat(Fill(p(XLen) - 16, 0.U), data(15, 0)),
        ctrl.is_word  -> {
          if (p(XLen) == 64) Cat(Fill(p(XLen) - 32, 0.U), data(31, 0))
          else data
        },
        ctrl.is_dword -> data
      )
    )

  def alignedStoreData(ctrl: StoreCtrl, addr: UInt, data: UInt)(implicit p: Parameters): UInt = {
    val raw = narrowStoreData(ctrl, data)
    (raw << (byteOffset(addr) << 3))(p(XLen) - 1, 0)
  }
}
