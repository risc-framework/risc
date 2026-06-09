package arch.core.bpu

import arch.configs._
import chisel3._
import chisel3.util.{ BitPat, log2Ceil }

trait BHTConsts {
  def BHT_X   = BitPat("b??")
  def SZ_BHT  = BHT_X.getWidth
  def BHT_SNT = BitPat("b00")
  def BHT_WNT = BitPat("b01")
  def BHT_WT  = BitPat("b10")
  def BHT_ST  = BitPat("b11")
}

class BpuUpdate(implicit p: Parameters) extends Bundle {
  val valid        = Bool()
  val pc           = UInt(p(XLen).W)
  val target       = UInt(p(XLen).W)
  val taken        = Bool()
  val mispredict   = Bool()
  val pht_index    = UInt(p(GShareGhrWidth).W)
  val ghr_snapshot = UInt(p(GShareGhrWidth).W)
}

class BpuIfuReq(implicit p: Parameters) extends Bundle {
  val query_pc      = Vec(p(IssueWidth), UInt(p(XLen).W))
  val advance_valid = Bool()
  val flush         = Bool()
}

class BpuIfuResp(implicit p: Parameters) extends Bundle {
  val taken        = Vec(p(IssueWidth), Bool())
  val target       = Vec(p(IssueWidth), UInt(p(XLen).W))
  val pht_index    = Vec(p(IssueWidth), UInt(p(GShareGhrWidth).W))
  val ghr_snapshot = Vec(p(IssueWidth), UInt(p(GShareGhrWidth).W))
}

class BtbEntry(tagWidth: Int)(implicit p: Parameters) extends Bundle with BHTConsts {
  val valid  = Bool()
  val tag    = UInt(tagWidth.W)
  val target = UInt(p(XLen).W)
  val ctrl   = UInt(SZ_BHT.W)
}

class BtbQueryReq(implicit p: Parameters) extends Bundle {
  val pc = Vec(p(IssueWidth), UInt(p(XLen).W))
}

class BtbQueryResp(implicit p: Parameters) extends Bundle with BHTConsts {
  private val rawIndexWidth = log2Ceil(p(BTBSets))
  private val tagWidth      = p(XLen) - rawIndexWidth - p(PCAlign)

  val hit       = Vec(p(IssueWidth), Bool())
  val entry_out = Vec(p(IssueWidth), new BtbEntry(tagWidth))
}

class PredictorQueryReq(implicit p: Parameters) extends Bundle {
  val pc        = Vec(p(IssueWidth), UInt(p(XLen).W))
  val is_branch = Vec(p(IssueWidth), Bool())
  val accept    = Bool()
  val flush     = Bool()
}

class PredictorQueryResp(implicit p: Parameters) extends Bundle {
  val taken        = Vec(p(IssueWidth), Bool())
  val pht_index    = Vec(p(IssueWidth), UInt(p(GShareGhrWidth).W))
  val ghr_snapshot = Vec(p(IssueWidth), UInt(p(GShareGhrWidth).W))
}
