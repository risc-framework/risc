package arch.core.bru.impls.isa.rv32i

import arch.core.bru._
import vutils.graph.{ NodeRegistry, RegisteredNodeUtils }
import arch.configs._
import chisel3._
import chisel3.util.{ BitPat, MuxLookup }

trait Rv32iBruUopConsts {
  private def cat(bps: BitPat*): BitPat = bps.reduce(_ ## _)
  private def N                         = BitPat("b0")
  private def Y                         = BitPat("b1")
  private def P_X                       = BitPat("b???")

  def B_X   = BitPat("b???")
  def SZ_B  = B_X.getWidth
  def B_EQ  = BitPat("b000")
  def B_NE  = BitPat("b001")
  def B_LT  = BitPat("b010")
  def B_GE  = BitPat("b011")
  def B_LTU = BitPat("b100")
  def B_GEU = BitPat("b101")
  def B_AL  = BitPat("b111")

  def UOP_BEQ  = cat(P_X, N, N, B_EQ)
  def UOP_BNE  = cat(P_X, N, N, B_NE)
  def UOP_BLT  = cat(P_X, N, N, B_LT)
  def UOP_BGE  = cat(P_X, N, N, B_GE)
  def UOP_BLTU = cat(P_X, N, N, B_LTU)
  def UOP_BGEU = cat(P_X, N, N, B_GEU)

  def UOP_JAL  = cat(P_X, Y, N, B_AL)
  def UOP_JALR = cat(P_X, Y, Y, B_AL)
}

object BruRv32iIsa extends RegisteredNodeUtils[BruIsaImpl] with Rv32iBruUopConsts {
  override def utils: BruIsaImpl = new BruIsaImpl with Rv32iBruUopConsts {
    override def value: String    = "rv32i"
    override def opWidth: Int     = SZ_B
    override def hasJump: Boolean = true
    override def hasJalr: Boolean = true

    override def decode(uop: UInt): BruCtrl = {
      val ctrl = Wire(new BruCtrl(opWidth))
      ctrl.is_jump := uop(4)
      ctrl.is_jalr := uop(3)
      ctrl.op      := uop(2, 0)
      ctrl
    }

    override def taken(src1: UInt, src2: UInt, op: UInt)(implicit p: Parameters): Bool = {
      val subRes = src1 +& ~src2 + 1.U
      val sum    = subRes(p(XLen) - 1, 0)
      val carry  = subRes(p(XLen))

      val eq = sum === 0.U
      val ne = !eq

      val sign1   = src1(p(XLen) - 1)
      val sign2   = src2(p(XLen) - 1)
      val sumSign = sum(p(XLen) - 1)

      val lt  = Mux(sign1 === sign2, sumSign, sign1)
      val ltu = !carry

      MuxLookup(op, false.B)(
        Seq(
          B_EQ.value.U(SZ_B.W)  -> eq,
          B_NE.value.U(SZ_B.W)  -> ne,
          B_LT.value.U(SZ_B.W)  -> lt,
          B_GE.value.U(SZ_B.W)  -> !lt,
          B_LTU.value.U(SZ_B.W) -> ltu,
          B_GEU.value.U(SZ_B.W) -> !ltu,
          B_AL.value.U(SZ_B.W)  -> true.B
        )
      )
    }
  }

  override def registry: NodeRegistry[BruIsaImpl] = BruIsaFactory
}
