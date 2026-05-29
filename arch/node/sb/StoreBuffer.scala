package arch.node.sb

import arch.configs._
import chisel3._
import chisel3.util.Decoupled

class StoreForwardReq(implicit p: Parameters) extends Bundle {
  val valid  = Bool()
  val addr   = UInt(p(XLen).W)
  val mask   = UInt(p(BytesPerWord).W)
  val sq_seq = UInt(64.W)
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
