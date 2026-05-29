package arch.node.uop

import arch.node.fupool.FunctionalUnitType
import arch.core.imm.ImmUtilsFactory
import arch.configs._
import chisel3._
import chisel3.util.log2Ceil

class MicroOp(implicit p: Parameters) extends Bundle {
  private val imm_utils = ImmUtilsFactory.getOrThrow(p(ISA).name)

  val pc    = UInt(p(XLen).W)
  val instr = UInt(p(ILen).W)

  val fu_type = UInt(log2Ceil(FunctionalUnitType.values.size).W)
  val fu_id   = UInt(log2Ceil(p(NumFUs)).W)

  val uop      = UInt(p(MicroOpWidth).W)
  val imm_type = UInt(imm_utils.immTypeWidth.W)

  val rs1 = UInt(log2Ceil(p(NumArchRegs)).W)
  val rs2 = UInt(log2Ceil(p(NumArchRegs)).W)
  val rd  = UInt(log2Ceil(p(NumArchRegs)).W)

  val rs1_valid = Bool()
  val rs2_valid = Bool()
  val rd_valid  = Bool()

  val rs1_data = UInt(p(XLen).W)
  val rs2_data = UInt(p(XLen).W)

  val rob_tag = UInt(p(RobTagWidth).W)

  val sq_idx = UInt(log2Ceil(p(StoreBufferSize)).W)
  val sq_seq = UInt(64.W)
}
