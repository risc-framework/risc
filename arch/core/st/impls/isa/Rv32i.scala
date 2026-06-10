package arch.core.st.impls.isa.rv32i

import arch.configs._
import arch.core.st._
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.{ BitPat, MuxLookup }

trait Rv32iMemUopConsts {
  private def cat(bps: BitPat*): BitPat = bps.reduce(_ ## _)
  private def N                         = BitPat("b0")
  private def Y                         = BitPat("b1")
  private def P_X                       = BitPat("b????")

  def MEM_X  = BitPat("b??")
  def SZ_MEM = MEM_X.getWidth

  def MEM_B = BitPat("b00")
  def MEM_H = BitPat("b01")
  def MEM_W = BitPat("b10")

  def UOP_LB  = cat(P_X, Y, N, MEM_B)
  def UOP_LH  = cat(P_X, Y, N, MEM_H)
  def UOP_LW  = cat(P_X, Y, N, MEM_W)
  def UOP_LBU = cat(P_X, Y, Y, MEM_B)
  def UOP_LHU = cat(P_X, Y, Y, MEM_H)

  def UOP_SB = cat(P_X, N, N, MEM_B)
  def UOP_SH = cat(P_X, N, N, MEM_H)
  def UOP_SW = cat(P_X, N, N, MEM_W)
}

object StRv32iIsa extends RegisteredNodeUtils[StIsaImpl] with Rv32iMemUopConsts {
  override def utils: StIsaImpl = new StIsaImpl with Rv32iMemUopConsts {
    override def value: String = "rv32i"

    override def decode(uop: UInt)(implicit p: Parameters): StoreCtrl = {
      val ctrl = Wire(new StoreCtrl)
      val size = uop(1, 0)

      ctrl.is_byte  := size === MEM_B.value.U(SZ_MEM.W)
      ctrl.is_half  := size === MEM_H.value.U(SZ_MEM.W)
      ctrl.is_word  := size === MEM_W.value.U(SZ_MEM.W)
      ctrl.is_dword := false.B

      ctrl.strb := MuxLookup(size, 0.U(p(BytesPerWord).W))(
        Seq(
          MEM_B.value.U(SZ_MEM.W) -> "b0001".U(p(BytesPerWord).W),
          MEM_H.value.U(SZ_MEM.W) -> "b0011".U(p(BytesPerWord).W),
          MEM_W.value.U(SZ_MEM.W) -> "b1111".U(p(BytesPerWord).W)
        )
      )

      ctrl
    }
  }

  override def registry: NodeDimensionRegistry[StIsaImpl] =
    StIsaFactory
}
