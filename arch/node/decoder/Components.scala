package arch.node.decoder

import arch.configs._
import arch.core.fu.FunctionalUnitType
import arch.node.imm.ImmIsaFactory
import chisel3._

class DecodedOutput(implicit p: Parameters) extends Bundle {
  private val imm = ImmIsaFactory.select(p(ISA).name)

  val legal          = Bool()
  val regwrite       = Bool()
  val rs1_valid      = Bool()
  val rs2_valid      = Bool()
  val rd_valid       = Bool()
  val commit_barrier = Bool()
  val imm_type       = UInt(imm.immTypeWidth.W)
  val fu_type        = UInt(p(FuTypeWidth).W)
  val uop            = UInt(p(MicroOpWidth).W)

  def isFu(t: arch.core.fu.FunctionalUnitType): Bool =
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

class DecoderDecodeIO(implicit p: Parameters) extends Bundle {
  val instr = Input(Vec(p(IssueWidth), UInt(p(ILen).W)))
  val out   = Output(Vec(p(IssueWidth), new DecodedOutput))
}
