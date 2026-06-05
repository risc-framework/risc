package arch.core.sb

import arch.configs._
import vcache.CachePortIO
import chisel3._
import chisel3.util.{ Decoupled, Valid, log2Ceil }

class StoreBufferTicket(implicit p: Parameters) extends Bundle {
  val sq_idx = UInt(log2Ceil(p(StoreBufferSize)).W)
  val sq_seq = UInt(64.W)
}

class StoreBufferRobCommitBundle(implicit p: Parameters) extends Bundle {
  val is_store = Bool()
  val sq_idx   = UInt(log2Ceil(p(StoreBufferSize)).W)
}

class StoreBufferRobIO(implicit p: Parameters) extends Bundle {
  val commit = Flipped(Vec(p(IssueWidth), Valid(new StoreBufferRobCommitBundle)))
}

class StoreWriteBundle(implicit p: Parameters) extends Bundle {
  val sq_idx    = UInt(log2Ceil(p(StoreBufferSize)).W)
  val rob_tag   = UInt(p(RobTagWidth).W)
  val addr      = UInt(p(XLen).W)
  val data      = UInt(p(XLen).W)
  val mask      = UInt(p(BytesPerWord).W)
  val cacheable = Bool()
}

class StoreForwardReq(implicit p: Parameters) extends Bundle {
  val valid  = Bool()
  val sq_seq = UInt(64.W)
  val addr   = UInt(p(XLen).W)
  val mask   = UInt(p(BytesPerWord).W)
}

class StoreForwardResp(implicit p: Parameters) extends Bundle {
  val block     = Bool()
  val has_older = Bool()
  val valid     = Bool()
  val full      = Bool()
  val data      = UInt(p(XLen).W)
  val mask      = UInt(p(BytesPerWord).W)
}

class StoreForwardIO(implicit p: Parameters) extends Bundle {
  val req  = Flipped(Decoupled(new StoreForwardReq))
  val resp = Decoupled(new StoreForwardResp)
}

class StoreBufferEntry(implicit p: Parameters) extends Bundle {
  val valid     = Bool()
  val committed = Bool()
  val addrValid = Bool()
  val fwdValid  = Bool()
  val seq       = UInt(64.W)
  val rob_tag   = UInt(p(RobTagWidth).W)
  val addr      = UInt(p(XLen).W)
  val data      = UInt(p(XLen).W)
  val mask      = UInt(p(BytesPerWord).W)
  val cacheable = Bool()
}

class StoreBufferWriteIO(numStorePorts: Int)(implicit p: Parameters) extends Bundle {
  val ports = Flipped(Vec(numStorePorts, Valid(new StoreWriteBundle)))
}

class StoreBufferForwardIO(numLoadPorts: Int)(implicit p: Parameters) extends Bundle {
  val ports = Vec(numLoadPorts, new StoreForwardIO)
}

class StoreBufferStateIO(implicit p: Parameters) extends Bundle {
  val tail        = Output(UInt(log2Ceil(p(StoreBufferSize)).W))
  val tailSeq     = Output(UInt(64.W))
  val freeCount   = Output(UInt(log2Ceil(p(StoreBufferSize) + 1).W))
  val empty       = Output(Bool())
  val busy        = Output(Bool())
  val oldestValid = Output(Bool())
  val oldestSeq   = Output(UInt(64.W))
}

class StoreBufferMemIO(implicit p: Parameters) extends Bundle {
  val mem  = new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams))
  val mmio = new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams))
}

class StoreBufferExceptionIO extends Bundle {
  val flush = Input(Bool())
}
