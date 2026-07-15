package arch.core.ld.impls.isa.rv32i

import arch.configs._
import arch.core.fupool.FuReq
import arch.core.ld._
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.{ BitPat, Cat, Fill, MuxLookup, log2Ceil }

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

    private def bytes(uop: FuReq): UInt =
      1.U << uop.uop(1, 0)

    private def unsigned(uop: FuReq): Bool =
      uop.uop(2)

    private def ea(uop: FuReq)(implicit p: Parameters): UInt = {
      require(p(XLen) == 32, "RV32I load address generation requires XLEN=32")

      // Load immediates are sign-extended 12-bit I-type values.  Compute the
      // low-word carry in parallel with both possible high-word adjustments,
      // instead of placing all 32 address bits on one carry chain.
      val lowSum     = uop.rs1_data(11, 0) +& uop.imm(11, 0)
      val upper      = uop.rs1_data(31, 12)
      val upperPlus  = upper + 1.U
      val upperMinus = upper - 1.U
      val adjustedUpper = Mux(
        uop.imm(11),
        Mux(lowSum(12), upper, upperMinus),
        Mux(lowSum(12), upperPlus, upper)
      )

      Cat(adjustedUpper, lowSum(11, 0))
    }

    private def packData(accessBytes: Int, data: UInt)(implicit p: Parameters): UInt = {
      val bits = accessBytes * 8
      val raw  = Cat((0 until accessBytes).reverse.map { lane =>
        val src = p(ISA).accessByteIndex(lane, accessBytes)
        data(8 * src + 7, 8 * src)
      })

      if (bits == p(XLen)) raw else Cat(0.U((p(XLen) - bits).W), raw)
    }

    private def loadData(accessBytes: Int, isUnsigned: Bool, addr: UInt, beatData: UInt)(implicit
      p: Parameters
    ): UInt = {
      val w       = log2Ceil(p(BytesPerWord) + 1)
      val off     = p(ISA).laneOffset(addr, accessBytes.U(w.W), p(BytesPerWord))
      val shifted = (beatData >> (off << 3))(p(XLen) - 1, 0)
      val packed  = packData(accessBytes, shifted)
      val bits    = accessBytes * 8

      if (bits == p(XLen)) {
        packed
      } else {
        Cat(Fill(p(XLen) - bits, packed(bits - 1) && !isUnsigned), packed(bits - 1, 0))
      }
    }

    private def loadData(bytes: UInt, isUnsigned: Bool, addr: UInt, beatData: UInt)(implicit
      p: Parameters
    ): UInt =
      MuxLookup(bytes, 0.U(p(XLen).W))(
        Seq(
          1.U -> loadData(1, isUnsigned, addr, beatData),
          2.U -> loadData(2, isUnsigned, addr, beatData),
          4.U -> loadData(4, isUnsigned, addr, beatData)
        )
      )

    override def addr(uop: FuReq)(implicit p: Parameters): UInt =
      p(ISA).beatAlignedAddr(ea(uop), p(BytesPerWord))

    override def data(uop: FuReq)(implicit p: Parameters): UInt = {
      val addr = ea(uop)
      val b    = bytes(uop)

      loadData(b, unsigned(uop), addr, uop.rs2_data)
    }

    override def mask(uop: FuReq)(implicit p: Parameters): UInt = {
      val addr = ea(uop)
      val off  = p(ISA).byteOffsetInBeat(addr, p(BytesPerWord))
      val base = MuxLookup(uop.uop(1, 0), 0.U(p(BytesPerWord).W))(
        Seq(
          LMEM(LMEM_B) -> 1.U(p(BytesPerWord).W),
          LMEM(LMEM_H) -> 3.U(p(BytesPerWord).W),
          LMEM(LMEM_W) -> 15.U(p(BytesPerWord).W)
        )
      )

      (base << off)(p(BytesPerWord) - 1, 0)
    }
  }

  override def registry: NodeDimensionRegistry[LdIsaImpl] =
    LdIsaFactory
}
