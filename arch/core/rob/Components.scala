package arch.core.rob

import arch.configs._
import arch.core.exception.{ ExceptionDims, ExceptionIsaFactory }
import vutils.graph.{ NodeConfig, NodeSelector }
import chisel3._
import chisel3.util.log2Ceil

class RobFuDone(implicit p: Parameters) extends Bundle {
  private val cfg = NodeConfig(selector = NodeSelector(ExceptionDims.ISA -> p(ISA).name))
  private val isa = ExceptionIsaFactory.select(cfg)

  val result       = UInt(p(XLen).W)
  val rob_tag      = UInt(p(RobTagWidth).W)
  val trap_req     = Bool()
  val trap_kind    = UInt(isa.kindWidth.W)
  val trap_target  = UInt(p(XLen).W)
  val trap_ret     = Bool()
  val trap_ret_tgt = UInt(p(XLen).W)
}

class RobBruResolved(implicit p: Parameters) extends Bundle {
  val valid       = Bool()
  val rob_tag     = UInt(p(RobTagWidth).W)
  val taken       = Bool()
  val target      = UInt(p(XLen).W)
  val fallthrough = UInt(p(XLen).W)
}

class RobSbCommit(implicit p: Parameters) extends Bundle {
  val is_store = Bool()
  val sq_idx   = UInt(log2Ceil(p(StoreBufferSize)).W)
}

class RobDebugInfo(implicit p: Parameters) extends Bundle {
  val commit_count   = UInt(log2Ceil(p(CommitWidth) + 1).W)
  val branch_commit  = UInt(log2Ceil(p(CommitWidth) + 1).W)
  val bpu_mispredict = Bool()
  val empty          = Bool()

  val instret  = Vec(p(CommitWidth), Bool())
  val pc       = Vec(p(CommitWidth), UInt(p(XLen).W))
  val instr    = Vec(p(CommitWidth), UInt(p(ILen).W))
  val reg_we   = Vec(p(CommitWidth), Bool())
  val reg_addr = Vec(p(CommitWidth), UInt(log2Ceil(p(NumArchRegs)).W))
  val reg_data = Vec(p(CommitWidth), UInt(p(XLen).W))
}

class RobEntry(implicit p: Parameters) extends Bundle {
  private val cfg = NodeConfig(selector = NodeSelector(ExceptionDims.ISA -> p(ISA).name))
  private val isa = ExceptionIsaFactory.select(cfg)

  val valid          = Bool()
  val ready          = Bool()
  val pc             = UInt(p(XLen).W)
  val instr          = UInt(p(ILen).W)
  val rd             = UInt(log2Ceil(p(NumArchRegs)).W)
  val rd_write       = Bool()
  val data           = UInt(p(XLen).W)
  val is_branch      = Bool()
  val is_store       = Bool()
  val commit_barrier = Bool()

  val pred_taken    = Bool()
  val pred_target   = UInt(p(XLen).W)
  val pht_index     = UInt(p(GShareGhrWidth).W)
  val ghr_snapshot  = UInt(p(GShareGhrWidth).W)
  val actual_taken  = Bool()
  val actual_target = UInt(p(XLen).W)

  val flush_pipeline = Bool()
  val flush_target   = UInt(p(XLen).W)

  val sq_idx = UInt(log2Ceil(p(StoreBufferSize)).W)

  val sync_valid = Bool()
  val sync_kind  = UInt(isa.kindWidth.W)
}

class RobCommitInfo(implicit p: Parameters) extends Bundle {
  private val cfg = NodeConfig(selector = NodeSelector(ExceptionDims.ISA -> p(ISA).name))
  private val isa = ExceptionIsaFactory.select(cfg)

  val valid          = Bool()
  val pop            = Bool()
  val pc             = UInt(p(XLen).W)
  val instr          = UInt(p(ILen).W)
  val rd             = UInt(log2Ceil(p(NumArchRegs)).W)
  val rd_write       = Bool()
  val data           = UInt(p(XLen).W)
  val flush_pipeline = Bool()
  val flush_target   = UInt(p(XLen).W)
  val is_branch      = Bool()
  val is_store       = Bool()
  val commit_barrier = Bool()

  val bpu_pred_taken    = Bool()
  val bpu_pred_target   = UInt(p(XLen).W)
  val bpu_actual_taken  = Bool()
  val bpu_actual_target = UInt(p(XLen).W)
  val bpu_pht_index     = UInt(p(GShareGhrWidth).W)
  val bpu_ghr_snapshot  = UInt(p(GShareGhrWidth).W)

  val sq_idx = UInt(log2Ceil(p(StoreBufferSize)).W)

  val sync_valid = Bool()
  val sync_kind  = UInt(isa.kindWidth.W)
}
