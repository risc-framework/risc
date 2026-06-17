package arch.core.bpu

import arch.configs._
import chisel3._
import chisel3.util.BitPat

object BpuBranchKind {
  val width = 3

  def NONE   = 0.U(width.W)
  def BRANCH = 1.U(width.W)
  def JUMP   = 2.U(width.W)
  def CALL   = 3.U(width.W)
  def RET    = 4.U(width.W)
  def CALL_RET = 5.U(width.W)

  def isUnconditional(kind: UInt): Bool =
    kind === JUMP || kind === CALL || kind === RET || kind === CALL_RET
}

trait BHTConsts {
  def BHT_SNT = BitPat("b00")
  def BHT_WNT = BitPat("b01")
  def BHT_WT  = BitPat("b10")
  def BHT_ST  = BitPat("b11")
  def SZ_BHT  = 2
}

class BtbEntry(implicit p: Parameters) extends Bundle with BHTConsts {
  val valid  = Bool()
  val tag    = UInt(p(XLen).W)
  val target = UInt(p(XLen).W)
  val ctrl   = UInt(SZ_BHT.W)
  val kind   = UInt(BpuBranchKind.width.W)
}

class BtbQueryReq(implicit p: Parameters) extends Bundle {
  val pc = Vec(p(IssueWidth), UInt(p(XLen).W))
}

class BtbQueryResp(implicit p: Parameters) extends Bundle {
  val hit       = Vec(p(IssueWidth), Bool())
  val entry_out = Vec(p(IssueWidth), new BtbEntry)
}

class PredictorQueryReq(implicit p: Parameters) extends Bundle {
  val pc        = Vec(p(IssueWidth), UInt(p(XLen).W))
  val accept    = Bool()
  val flush     = Bool()
  val is_branch = Vec(p(IssueWidth), Bool())
}

class PredictorQueryResp(implicit p: Parameters) extends Bundle {
  val taken        = Vec(p(IssueWidth), Bool())
  val pht_index    = Vec(p(IssueWidth), UInt(p(GShareGhrWidth).W))
  val ghr_snapshot = Vec(p(IssueWidth), UInt(p(GShareGhrWidth).W))
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

class BpuUpdate(implicit p: Parameters) extends Bundle {
  val valid        = Bool()
  val pc           = UInt(p(XLen).W)
  val target       = UInt(p(XLen).W)
  val taken        = Bool()
  val branch_kind  = UInt(BpuBranchKind.width.W)
  val pht_index    = UInt(p(GShareGhrWidth).W)
  val ghr_snapshot = UInt(p(GShareGhrWidth).W)
  val mispredict   = Bool()
}
