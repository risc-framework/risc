package arch.core.fupool

import arch.configs._
import arch.core.csr.InterruptLines
import arch.core.decode.{ DecodeDims, DecodeIsaFactory }
import arch.core.exception.{ ExceptionDims, ExceptionIsaFactory }
import vutils.graph.{ NodeConfig, NodeSelector }
import chisel3._
import chisel3.util.log2Ceil

class FuReq(implicit p: Parameters) extends Bundle {
  private val cfg = NodeConfig(
    selector = NodeSelector(
      DecodeDims.ISA -> p(ISA).name
    )
  )

  private val isa = DecodeIsaFactory.select(cfg)

  val pc    = UInt(p(XLen).W)
  val instr = UInt(p(ILen).W)

  val fu_type = UInt(p(FuTypeWidth).W)
  val fu_id   = UInt(log2Ceil(p(NumFUs)).W)

  val uop = UInt(isa.uopWidth.W)
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
  private val cfg = NodeConfig(selector = NodeSelector(ExceptionDims.ISA -> p(ISA).name))
  private val isa = ExceptionIsaFactory.select(cfg)

  val result  = UInt(p(XLen).W)
  val rd      = UInt(log2Ceil(p(NumArchRegs)).W)
  val pc      = UInt(p(XLen).W)
  val instr   = UInt(p(ILen).W)
  val rob_tag = UInt(p(RobTagWidth).W)

  val trap_req    = Bool()
  val trap_kind   = UInt(isa.kindWidth.W)
  val trap_target = UInt(p(XLen).W)
}

class FuPoolCpuReq extends Bundle {
  val cycle   = UInt(64.W)
  val instret = UInt(64.W)
  val irq     = new InterruptLines
}
