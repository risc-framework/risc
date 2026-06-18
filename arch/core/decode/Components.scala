package arch.core.decode

import arch.core.ifu.IBufferEntry
import arch.core.fupool.FunctionalUnitType
import arch.configs._
import vutils.graph.{ NodeConfig, NodeSelector }
import chisel3._
import chisel3.util.log2Ceil

class DecodePacket(implicit p: Parameters) extends IBufferEntry

class DecodedPacket(implicit p: Parameters) extends IBufferEntry {
  private val cfg = NodeConfig(
    selector = NodeSelector(
      DecodeDims.ISA -> p(ISA).name
    )
  )

  private val isa = DecodeIsaFactory.select(cfg)

  val legal          = Bool()
  val regwrite       = Bool()
  val rs1_read       = Bool()
  val rs2_read       = Bool()
  val rd_write       = Bool()
  val commit_barrier = Bool()

  val rs1 = UInt(log2Ceil(p(NumArchRegs)).W)
  val rs2 = UInt(log2Ceil(p(NumArchRegs)).W)
  val rd  = UInt(log2Ceil(p(NumArchRegs)).W)

  val imm     = UInt(p(XLen).W)
  val fu_type = UInt(p(FuTypeWidth).W)
  val uop     = UInt(isa.uopWidth.W)

  def isFu(t: FunctionalUnitType): Bool =
    fu_type === t.index.U(p(FuTypeWidth).W)

  def isLoad: Bool =
    isFu(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD)

  def isStore: Bool =
    isFu(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST)

  def isBru: Bool =
    isFu(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_BRU)

  def isCsr: Bool =
    isFu(FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR)
}
