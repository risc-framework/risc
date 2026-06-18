package arch.core.ld.impls.isa.rv32i

import arch.configs._
import arch.core.ld._
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.{ BitPat, MuxLookup }

trait Rv32iLdUopConsts {
  private def cat(bps: BitPat*): BitPat = bps.reduce(_ ## _)
  private def N                         = BitPat("b0")
  private def Y                         = BitPat("b1")
  private def P_X                       = BitPat("b?????")

  def LMEM_X  = BitPat("b??")
  def SZ_LMEM = LMEM_X.getWidth
  def LMEM_B  = BitPat("b00")
  def LMEM_H  = BitPat("b01")
  def LMEM_W  = BitPat("b10")

  def LMEM(size: BitPat): UInt = size.value.U(SZ_LMEM.W)

  def UOP_LB  = cat(P_X, N, LMEM_B)
  def UOP_LH  = cat(P_X, N, LMEM_H)
  def UOP_LW  = cat(P_X, N, LMEM_W)
  def UOP_LBU = cat(P_X, Y, LMEM_B)
  def UOP_LHU = cat(P_X, Y, LMEM_H)
}

object LdRv32iIsa extends RegisteredNodeUtils[LdIsaImpl] with Rv32iLdUopConsts {
  override def utils: LdIsaImpl = new LdIsaImpl with Rv32iLdUopConsts {
    override def value: String = "rv32i"

    override def decode(uop: UInt)(implicit p: Parameters): LoadCtrl = {
      val ctrl = Wire(new LoadCtrl)
      val size = uop(1, 0)

      ctrl.is_byte     := size === LMEM(LMEM_B)
      ctrl.is_half     := size === LMEM(LMEM_H)
      ctrl.is_word     := size === LMEM(LMEM_W)
      ctrl.is_dword    := false.B
      ctrl.is_unsigned := uop(2)

      ctrl.strb := MuxLookup(size, 0.U(p(BytesPerWord).W))(
        Seq(
          LMEM(LMEM_B) -> "b0001".U(p(BytesPerWord).W),
          LMEM(LMEM_H) -> "b0011".U(p(BytesPerWord).W),
          LMEM(LMEM_W) -> "b1111".U(p(BytesPerWord).W)
        )
      )

      ctrl
    }
  }

  override def registry: NodeDimensionRegistry[LdIsaImpl] =
    LdIsaFactory
}
