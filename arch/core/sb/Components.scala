package arch.core.sb

import arch.configs._
import chisel3._
import chisel3.util.log2Ceil

class StoreBufferTicket(implicit p: Parameters) extends Bundle {
  val sq_idx = UInt(log2Ceil(p(StoreBufferSize)).W)
  val sq_seq = UInt(64.W)
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

class StoreBufferStatus extends Bundle {
  val oldest_valid = Bool()
  val oldest_seq   = UInt(64.W)
}

class StoreBufferExceptionReq extends Bundle {
  val flush = Bool()
}
