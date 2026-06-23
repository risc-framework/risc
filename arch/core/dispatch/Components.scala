package arch.core.dispatch

import arch.configs._
import arch.core.decode.DecodedPacket
import arch.core.sb.StoreBufferTicket
import chisel3._
import chisel3.util.log2Ceil

class DispatchRobPacket(implicit p: Parameters) extends Bundle {
  val decoded = new DecodedPacket
  val sq_idx  = UInt(log2Ceil(p(StoreBufferSize)).W)
}

class DispatchRobResp(implicit p: Parameters) extends Bundle {
  val rob_tag = UInt(p(RobTagWidth).W)

  val rs1_bypass_valid   = Bool()
  val rs1_bypass_data    = UInt(p(XLen).W)
  val rs1_bypass_pending = Bool()

  val rs2_bypass_valid   = Bool()
  val rs2_bypass_data    = UInt(p(XLen).W)
  val rs2_bypass_pending = Bool()
}

class DispatchStoreBufferReq(implicit p: Parameters) extends Bundle {
  val valid   = Bool()
  val bits    = new DecodedPacket
  val fire    = Bool()
  val rob_tag = UInt(p(RobTagWidth).W)
}

class DispatchStoreBufferResp(implicit p: Parameters) extends Bundle {
  val ready  = Bool()
  val ticket = new StoreBufferTicket
}
