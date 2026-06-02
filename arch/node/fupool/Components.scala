package arch.node.fupool

import arch.core.fu.FunctionalUnitType
import arch.node.uop.MicroOp
import arch.node.bru.BruResolveIO
import arch.node.st.StSbWriteIO
import arch.node.ld.{ LdMemIO, LdSbFwdIO }
import arch.node.csr.CsrCtrlIO
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
  private val n = p(FunctionalUnits).count(_.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD)
  val ports     = Vec(n, new LdMemIO)
}

class VecLdSbFwdIO(implicit p: Parameters) extends Bundle {
  private val n = p(FunctionalUnits).count(_.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD)
  val ports     = Vec(n, new LdSbFwdIO)
}

class VecStSbWriteIO(implicit p: Parameters) extends Bundle {
  private val n = p(FunctionalUnits).count(_.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST)
  val ports     = Vec(n, new StSbWriteIO)
}

class VecBruResolveIO(implicit p: Parameters) extends Bundle {
  private val n = p(FunctionalUnits).count(_.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_BRU)
  val ports     = Vec(n, new BruResolveIO)
}

class VecCsrCtrlIO(implicit p: Parameters) extends Bundle {
  private val numCsrFUs = p(FunctionalUnits).count(
    _.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR
  )

  val ports = Vec(numCsrFUs, new CsrCtrlIO)
}
