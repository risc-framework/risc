package arch.core.st.impls.isa.rv32i

import arch.configs._
import arch.core.fupool.FuReq
import arch.core.st._
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.{ BitPat, Cat, MuxLookup }

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

    private def bytes(uop: FuReq): UInt =
      1.U << uop.uop(1, 0)

    private def ea(uop: FuReq): UInt =
      uop.rs1_data + uop.imm

    private def rawMask(bytes: UInt)(implicit p: Parameters): UInt =
      ((1.U((p(BytesPerWord) + 1).W) << bytes) - 1.U)(p(BytesPerWord) - 1, 0)

    private def packData(accessBytes: Int, data: UInt)(implicit p: Parameters): UInt = {
      val bits = accessBytes * 8
      val raw  = Cat((0 until accessBytes).reverse.map { lane =>
        val src = p(ISA).accessByteIndex(lane, accessBytes)
        data(8 * src + 7, 8 * src)
      })

      if (bits == p(XLen)) raw else Cat(0.U((p(XLen) - bits).W), raw)
    }

    private def rawData(bytes: UInt, data: UInt)(implicit p: Parameters): UInt =
      MuxLookup(bytes, 0.U(p(XLen).W))(
        Seq(
          1.U -> packData(1, data),
          2.U -> packData(2, data),
          4.U -> packData(4, data)
        )
      )

    override def addr(uop: FuReq)(implicit p: Parameters): UInt =
      p(ISA).beatAlignedAddr(ea(uop), p(BytesPerWord))

    override def data(uop: FuReq)(implicit p: Parameters): UInt = {
      val addr = ea(uop)
      val b    = bytes(uop)
      val off  = p(ISA).laneOffset(addr, b, p(BytesPerWord))

      (rawData(b, uop.rs2_data) << (off << 3))(p(XLen) - 1, 0)
    }

    override def mask(uop: FuReq)(implicit p: Parameters): UInt = {
      val addr = ea(uop)
      val b    = bytes(uop)
      val off  = p(ISA).laneOffset(addr, b, p(BytesPerWord))

      (rawMask(b) << off)(p(BytesPerWord) - 1, 0)
    }
  }

  override def registry: NodeDimensionRegistry[StIsaImpl] =
    StIsaFactory
}
