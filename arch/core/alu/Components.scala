package arch.core.alu

import chisel3._
import chisel3.util.BitPat

trait AluConsts {
  def A1_X    = BitPat("b??")
  def SZ_A1   = A1_X.getWidth
  def A1_ZERO = BitPat("b00")
  def A1_RS1  = BitPat("b01")
  def A1_PC   = BitPat("b10")

  def A1(sel: BitPat): UInt = sel.value.U(SZ_A1.W)

  def A2_X      = BitPat("b??")
  def SZ_A2     = A2_X.getWidth
  def A2_ZERO   = BitPat("b00")
  def A2_RS2    = BitPat("b01")
  def A2_IMM    = BitPat("b10")
  def A2_PCSTEP = BitPat("b11")

  def A2(sel: BitPat): UInt = sel.value.U(SZ_A2.W)
}

class AluCtrl(fnWidth: Int) extends Bundle with AluConsts {
  val sel1 = UInt(SZ_A1.W)
  val sel2 = UInt(SZ_A2.W)
  val mode = Bool()
  val fn   = UInt(fnWidth.W)
}
