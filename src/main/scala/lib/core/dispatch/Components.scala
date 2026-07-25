package arch.core.dispatch

import arch.configs._
import arch.core.decode.DecodedPacket
import chisel3._
import chisel3.util.log2Ceil

class DispatchRobPacket(implicit p: Parameters) extends Bundle {
  val active  = Bool()
  val decoded = new DecodedPacket
}

class DispatchRobResp(implicit p: Parameters) extends Bundle {
  val rob_tag = UInt(p(RobTagWidth).W)
  val sq_idx  = UInt(log2Ceil(p(StoreBufferSize)).W)
  val sq_seq  = UInt(p(StoreSeqWidth).W)

  val rs1_bypass_valid   = Bool()
  val rs1_bypass_data    = UInt(p(XLen).W)
  val rs1_bypass_pending = Bool()
  val rs1_bypass_tag     = UInt(p(RobTagWidth).W)

  val rs2_bypass_valid   = Bool()
  val rs2_bypass_data    = UInt(p(XLen).W)
  val rs2_bypass_pending = Bool()
  val rs2_bypass_tag     = UInt(p(RobTagWidth).W)
}
