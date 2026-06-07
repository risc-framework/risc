package arch.core.fupool

import arch.core.bru.BruResolveIO
import arch.core.csr.{ CsrTrapUpdate, CsrTrapView, InterruptLines }
import arch.core.sb.{ StoreForwardIO, StoreWriteBundle }
import arch.configs._
import chisel3._
import chisel3.util.{ Decoupled, Valid, log2Ceil }
import vcache.CachePortIO

class FuReq(implicit p: Parameters) extends Bundle {
  val pc    = UInt(p(XLen).W)
  val instr = UInt(p(ILen).W)

  val fu_type = UInt(log2Ceil(FunctionalUnitType.values.size).W)
  val fu_id   = UInt(log2Ceil(p(NumFUs)).W)

  val uop = UInt(p(MicroOpWidth).W)
  val imm = UInt(p(XLen).W)

  val rs1 = UInt(log2Ceil(p(NumArchRegs)).W)
  val rs2 = UInt(log2Ceil(p(NumArchRegs)).W)
  val rd  = UInt(log2Ceil(p(NumArchRegs)).W)

  val rs1_read = Bool()
  val rs2_read = Bool()
  val rd_write = Bool()

  val rs1_data = UInt(p(XLen).W)
  val rs2_data = UInt(p(XLen).W)

  val rob_tag = UInt(p(RobTagWidth).W)

  val sq_idx = UInt(log2Ceil(p(StoreBufferSize)).W)
  val sq_seq = UInt(64.W)
}

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
  val req   = Flipped(Decoupled(new FuReq))
  val resp  = Decoupled(new FuResp)
  val flush = Input(Bool())
}

class FuPoolCpuIO extends Bundle {
  val cycle   = Input(UInt(64.W))
  val instret = Input(UInt(64.W))
  val irq     = Input(new InterruptLines)
}

class FuPoolSchedulerIO(implicit p: Parameters) extends Bundle {
  val reqs = Vec(p(NumFUs), Flipped(Decoupled(new FuReq)))
  val done = Output(Vec(p(NumFUs), Valid(new FuResp)))
}

class FuPoolRobIO(implicit p: Parameters) extends Bundle {
  val done = Output(Vec(p(NumFUs), Valid(new FuResp)))
  val bru  = Output(Vec(p(NumBRUs), new BruResolveIO))
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

class FuPoolExceptionIO(implicit p: Parameters) extends Bundle {
  val flush       = Input(Bool())
  val arch_pc     = Input(UInt(p(XLen).W))
  val trap_update = Input(new CsrTrapUpdate)
  val csr_busy    = Output(Bool())
}

class FuPoolInterruptIO(implicit p: Parameters) extends Bundle {
  val view = Output(new CsrTrapView)
}
