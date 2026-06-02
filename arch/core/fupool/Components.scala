package arch.core.fupool

import arch.core.uop.MicroOp
import arch.core.bru.BruResolveIO
import arch.core.st.StSbWriteIO
import arch.core.ld.{ LdMemIO, LdSbFwdIO }
import arch.core.csr.CsrCtrlIO
import arch.configs._
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

class FuPoolFuIO(implicit p: Parameters) extends Bundle {
  val req   = Vec(p(NumFUs), Flipped(Decoupled(new MicroOp)))
  val done  = Output(Vec(p(NumFUs), Valid(new FuResp)))
  val flush = Input(Bool())
}

class VecLdMemIO(implicit p: Parameters) extends Bundle {
  val ports = Vec(p(NumLDs), new LdMemIO)
}

class VecLdSbFwdIO(implicit p: Parameters) extends Bundle {
  val ports = Vec(p(NumLDs), new LdSbFwdIO)
}

class VecStSbWriteIO(implicit p: Parameters) extends Bundle {
  val ports = Vec(p(NumSTs), new StSbWriteIO)
}

class VecBruResolveIO(implicit p: Parameters) extends Bundle {
  val ports = Vec(p(NumBRUs), new BruResolveIO)
}

class VecCsrCtrlIO(implicit p: Parameters) extends Bundle {
  val ports = Vec(p(NumCSRs), new CsrCtrlIO)
}
