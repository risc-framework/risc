package arch.core.st.impls.isa.rv32i

import arch.configs._
import arch.core.st._
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.{ BitPat, MuxLookup }

trait Rv32iStUopConsts {
  private def cat(bps: BitPat*): BitPat = bps.reduce(_ ## _)
  private def P_X                       = BitPat("b??????")

  def SMEM_X  = BitPat("b??")
  def SZ_SMEM = SMEM_X.getWidth
  def SMEM_B  = BitPat("b00")
  def SMEM_H  = BitPat("b01")
  def SMEM_W  = BitPat("b10")

  def SMEM(size: BitPat): UInt = size.value.U(SZ_SMEM.W)

  def UOP_SB = cat(P_X, SMEM_B)
  def UOP_SH = cat(P_X, SMEM_H)
  def UOP_SW = cat(P_X, SMEM_W)
}

object StRv32iIsa extends RegisteredNodeUtils[StIsaImpl] with Rv32iStUopConsts {
  override def utils: StIsaImpl = new StIsaImpl with Rv32iStUopConsts {
    override def value: String = "rv32i"

    override def decode(uop: UInt)(implicit p: Parameters): StoreCtrl = {
      val ctrl = Wire(new StoreCtrl)
      val size = uop(1, 0)

      ctrl.is_byte  := size === SMEM(SMEM_B)
      ctrl.is_half  := size === SMEM(SMEM_H)
      ctrl.is_word  := size === SMEM(SMEM_W)
      ctrl.is_dword := false.B

      ctrl.strb := MuxLookup(size, 0.U(p(BytesPerWord).W))(
        Seq(
          SMEM(SMEM_B) -> "b0001".U(p(BytesPerWord).W),
          SMEM(SMEM_H) -> "b0011".U(p(BytesPerWord).W),
          SMEM(SMEM_W) -> "b1111".U(p(BytesPerWord).W)
        )
      )

      ctrl
    }
  }

  override def registry: NodeDimensionRegistry[StIsaImpl] =
    StIsaFactory
}
