package arch.node.fupool

import arch.node.uop.MicroOp
import arch.configs._
import chisel3._
import chisel3.util.{ Decoupled, Valid, log2Ceil }

class FuResp(implicit p: Parameters) extends Bundle {
  val result  = UInt(p(XLen).W)
  val rd      = UInt(log2Ceil(p(NumArchRegs)).W)
  val pc      = UInt(p(XLen).W)
  val instr   = UInt(p(ILen).W)
  val rob_tag = UInt(p(RobTagWidth).W)
}

class FuIO(implicit p: Parameters) extends Bundle {
  val req   = Flipped(Decoupled(new MicroOp))
  val resp  = Decoupled(new FuResp)
  val flush = Input(Bool())
}

class FuPoolFuIO(implicit p: Parameters) extends Bundle {
  val req   = Vec(p(NumFUs), Flipped(Decoupled(new MicroOp)))
  val done  = Output(Vec(p(NumFUs), Valid(new FuResp)))
  val flush = Input(Bool())
}
