package arch.core.dispatch

import arch.configs._
import arch.core.decode.DecodedPacket
import arch.core.uop.MicroOp
import arch.core.sb.StoreBufferTicket
import chisel3._
import chisel3.util.{ Decoupled, log2Ceil }

class DispatchDecodeLaneIO(implicit p: Parameters) extends Bundle {
  val valid = Input(Bool())
  val bits  = Input(new DecodedPacket)
  val ready = Output(Bool())
}

class DispatchDecodeIO(implicit p: Parameters) extends Bundle {
  val lanes = Vec(p(IssueWidth), new DispatchDecodeLaneIO)
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
  val req_valid = Output(Bool())
  val req_ready = Input(Bool())
  val req_bits  = Output(new DispatchRobPacket)

  val rob_tag = Input(UInt(p(RobTagWidth).W))

  val rs1_bypass_valid   = Input(Bool())
  val rs1_bypass_data    = Input(UInt(p(XLen).W))
  val rs1_bypass_pending = Input(Bool())

  val rs2_bypass_valid   = Input(Bool())
  val rs2_bypass_data    = Input(UInt(p(XLen).W))
  val rs2_bypass_pending = Input(Bool())
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

class DispatchSchedulerIO(implicit p: Parameters) extends Bundle {
  val reqs = Vec(p(IssueWidth), Decoupled(new MicroOp))
}

class DispatchExceptionIO extends Bundle {
  val flush = Input(Bool())
}
