package arch.core.sb

import arch.configs._
import chisel3._
import chisel3.util.log2Ceil

object StoreBufferSequence {
  def isOlder(lhs: UInt, rhs: UInt)(implicit p: Parameters): Bool = {
    val distance = rhs - lhs
    lhs =/= rhs && !distance(p(StoreSeqWidth) - 1)
  }
}

class StoreBufferTicket(implicit p: Parameters) extends Bundle {
  val sq_idx = UInt(log2Ceil(p(StoreBufferSize)).W)
  val sq_seq = UInt(p(StoreSeqWidth).W)
}

class StoreBufferAllocStatus(implicit p: Parameters) extends Bundle {
  val free_count = UInt(log2Ceil(p(StoreBufferSize) + 1).W)
  val tail       = UInt(log2Ceil(p(StoreBufferSize)).W)
  val tail_seq   = UInt(p(StoreSeqWidth).W)
}

class StoreBufferAllocReq(implicit p: Parameters) extends Bundle {
  val sq_idx = UInt(log2Ceil(p(StoreBufferSize)).W)
  val sq_seq = UInt(p(StoreSeqWidth).W)
}

class StoreWriteBundle(implicit p: Parameters) extends Bundle {
  val sq_idx    = UInt(log2Ceil(p(StoreBufferSize)).W)
  val addr      = UInt(p(XLen).W)
  val data      = UInt(p(XLen).W)
  val mask      = UInt(p(BytesPerWord).W)
  val cacheable = Bool()
}

class StoreForwardReq(implicit p: Parameters) extends Bundle {
  val sq_seq = UInt(p(StoreSeqWidth).W)
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
  val seq       = UInt(p(StoreSeqWidth).W)
  val addr      = UInt(p(XLen).W)
  val data      = UInt(p(XLen).W)
  val mask      = UInt(p(BytesPerWord).W)
  val cacheable = Bool()
}

class StoreBufferStatus(implicit p: Parameters) extends Bundle {
  val oldest_valid = Bool()
  val oldest_seq   = UInt(p(StoreSeqWidth).W)
}

class StoreBufferDebugInfo extends Bundle {
  val busy       = Bool()
  val wait_drain = Bool()
}
