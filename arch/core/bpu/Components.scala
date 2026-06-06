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
  val pht_index    = UInt(p(GShareGhrWidth).W)
  val ghr_snapshot = UInt(p(GShareGhrWidth).W)
  val mispredict   = Bool()
}

class BpuIfuIO(implicit p: Parameters) extends Bundle {
  val query_pc      = Input(Vec(p(IssueWidth), UInt(p(XLen).W)))
  val advance_valid = Input(Bool())
  val flush         = Input(Bool())
  val taken         = Output(Vec(p(IssueWidth), Bool()))
  val target        = Output(Vec(p(IssueWidth), UInt(p(XLen).W)))
  val pht_index     = Output(Vec(p(IssueWidth), UInt(p(GShareGhrWidth).W)))
  val ghr_snapshot  = Output(Vec(p(IssueWidth), UInt(p(GShareGhrWidth).W)))
}

class BpuRobIO(implicit p: Parameters) extends Bundle {
  val update = Input(new BpuUpdate)
}

class BpuUpdateIO(implicit p: Parameters) extends Bundle {
  val update = Input(new BpuUpdate)
}

class BtbEntry(tagWidth: Int)(implicit p: Parameters) extends Bundle with BHTConsts {
  val valid  = Bool()
  val tag    = UInt(tagWidth.W)
  val target = UInt(p(XLen).W)
  val ctrl   = UInt(SZ_BHT.W)
}

class BtbQueryIO(implicit p: Parameters) extends Bundle with BHTConsts {
  private val rawIndexWidth = log2Ceil(p(BTBSets))
  private val tagWidth      = p(XLen) - rawIndexWidth - p(PCAlign)

  val pc        = Input(Vec(p(IssueWidth), UInt(p(XLen).W)))
  val hit       = Output(Vec(p(IssueWidth), Bool()))
  val entry_out = Output(Vec(p(IssueWidth), new BtbEntry(tagWidth)))
}

class PredictorQueryIO(implicit p: Parameters) extends Bundle {
  val pc           = Input(Vec(p(IssueWidth), UInt(p(XLen).W)))
  val is_branch    = Input(Vec(p(IssueWidth), Bool()))
  val accept       = Input(Bool())
  val flush        = Input(Bool())
  val taken        = Output(Vec(p(IssueWidth), Bool()))
  val pht_index    = Output(Vec(p(IssueWidth), UInt(p(GShareGhrWidth).W)))
  val ghr_snapshot = Output(Vec(p(IssueWidth), UInt(p(GShareGhrWidth).W)))
}

class PredictorUpdateIO(implicit p: Parameters) extends Bundle {
  val update = Input(new BpuUpdate)
}
