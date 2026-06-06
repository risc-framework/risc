package arch.core.fupool

import arch.core.uop.MicroOp
import arch.core.bru.BruResolveIO
import arch.core.csr.CsrCtrlIO
import arch.core.sb.{ StoreForwardIO, StoreWriteBundle }
import arch.configs._
import vcache.CachePortIO
import chisel3._
import chisel3.util.{ Decoupled, Valid, log2Ceil }

class FuResp(implicit p: Parameters) extends Bundle {
  val result  = UInt(p(XLen).W)
  val rd      = UInt(log2Ceil(p(NumArchRegs)).W)
  val pc      = UInt(p(XLen).W)
  val instr   = UInt(p(ILen).W)
  val rob_tag = UInt(p(RobTagWidth).W)

  val trap_req     = Bool()
  val trap_target  = UInt(p(XLen).W)
  val trap_ret     = Bool()
  val trap_ret_tgt = UInt(p(XLen).W)
}

class FuIO(implicit p: Parameters) extends Bundle {
  val req   = Flipped(Decoupled(new MicroOp))
  val resp  = Decoupled(new FuResp)
  val flush = Input(Bool())
}

class FuPoolSchedulerIO(implicit p: Parameters) extends Bundle {
  val reqs = Vec(p(NumFUs), Flipped(Decoupled(new MicroOp)))
  val done = Output(Vec(p(NumFUs), Valid(new FuResp)))
}

class FuPoolRobIO(implicit p: Parameters) extends Bundle {
  val done = Output(Vec(p(NumFUs), Valid(new FuResp)))
  val bru  = Vec(p(NumBRUs), new BruResolveIO)
}

class FuPoolMemoryArbiterIO(implicit p: Parameters) extends Bundle {
  val load_mem  = Vec(p(NumLDs), new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams)))
  val load_mmio = Vec(p(NumLDs), new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams)))
}

class FuPoolSbIO(implicit p: Parameters) extends Bundle {
  val fwd          = Vec(p(NumLDs), Flipped(new StoreForwardIO))
  val oldest_valid = Input(Bool())
  val oldest_seq   = Input(UInt(64.W))
  val write        = Output(Vec(p(NumSTs), Valid(new StoreWriteBundle)))
}

class FuPoolExceptionIO extends Bundle {
  val flush = Input(Bool())
}

class VecCsrCtrlIO(implicit p: Parameters) extends Bundle {
  val ports = Vec(p(NumCSRs), new CsrCtrlIO)
}
