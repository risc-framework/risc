package arch.core.dispatch

import arch.configs._
import arch.core.decode.DecodedPacket
import arch.core.rob.RobBypassResp
import arch.core.sb.StoreBufferTicket
import chisel3._
import chisel3.util.{ Decoupled, log2Ceil }

class DispatchDecodeIO(implicit p: Parameters) extends Bundle {
  val lanes = Flipped(Vec(p(IssueWidth), Decoupled(new DecodedPacket)))
}

class DispatchRegfileIO(implicit p: Parameters) extends Bundle {
  val rs1_addr = Output(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
  val rs2_addr = Output(Vec(p(IssueWidth), UInt(log2Ceil(p(NumArchRegs)).W)))
  val rs1_data = Input(Vec(p(IssueWidth), UInt(p(XLen).W)))
  val rs2_data = Input(Vec(p(IssueWidth), UInt(p(XLen).W)))
}

class DispatchRobPacket(implicit p: Parameters) extends Bundle {
  val decoded = new DecodedPacket
  val sq_idx  = UInt(log2Ceil(p(StoreBufferSize)).W)
}

class DispatchRobLaneIO(implicit p: Parameters) extends Bundle {
  val req        = Decoupled(new DispatchRobPacket)
  val rob_tag    = Input(UInt(p(RobTagWidth).W))
  val rs1_bypass = Flipped(new RobBypassResp)
  val rs2_bypass = Flipped(new RobBypassResp)
}

class DispatchRobIO(implicit p: Parameters) extends Bundle {
  val lanes = Vec(p(IssueWidth), new DispatchRobLaneIO)
}

class DispatchStoreBufferLaneIO(implicit p: Parameters) extends Bundle {
  val valid   = Output(Bool())
  val bits    = Output(new DecodedPacket)
  val fire    = Output(Bool())
  val rob_tag = Output(UInt(p(RobTagWidth).W))
  val ready   = Input(Bool())
  val ticket  = Input(new StoreBufferTicket)
}

class DispatchStoreBufferIO(implicit p: Parameters) extends Bundle {
  val lanes = Vec(p(IssueWidth), new DispatchStoreBufferLaneIO)
}

class DispatchExceptionIO extends Bundle {
  val flush = Input(Bool())
}
