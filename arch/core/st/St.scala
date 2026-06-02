package arch.core.st

import arch.core.pma.PmaModeFactory
import arch.core.fupool.FuResp
import arch.core.imm.ImmIsaFactory
import arch.core.uop.MicroOp
import arch.core.fupool.FuIO
import arch.configs._
import vutils.graph.{ Node, NodeConfig, NodeSelector, NodeType }
import chisel3._
import chisel3.util.{ is, switch }

class StIO(implicit p: Parameters) extends Bundle {
  val fu = new FuIO
  val sb = new StSbWriteIO
}

object StState extends ChiselEnum {
  val IDLE, WRITE_SB, DONE = Value
}

class St(implicit p: Parameters) extends Node(new StIO) {
  private val cfg = NodeConfig(
    selector = NodeSelector(
      StDims.ISA -> p(ISA).name
    )
  )

  override def nodeType: NodeType  = StMeta.Type
  override def desiredName: String = s"st_${cfg.selector.canonicalName}"

  private val isaImpl = StIsaFactory.select(cfg)
  private val imm     = ImmIsaFactory.select(p(ISA).name)
  private val pma     = PmaModeFactory.select("default")
  private val state   = RegInit(StState.IDLE)
  private val uopReg  = Reg(new MicroOp)

  private val ctrl        = isaImpl.decodeStore(uopReg.uop)
  private val addr        = uopReg.rs1_data + imm.gen(uopReg.instr, uopReg.imm_type)
  private val alignedAddr = isaImpl.alignedAddr(addr)
  private val storeData   = isaImpl.alignedStoreData(ctrl, addr, uopReg.rs2_data)
  private val storeMask   = isaImpl.shiftedStoreMask(ctrl, addr)
  private val pmaResult   = pma.check(addr)

  io.fu.req.ready := !io.fu.flush && (state === StState.IDLE || (state === StState.DONE && io.fu.resp.ready))

  private val acceptFire = io.fu.req.fire && !io.fu.flush

  io.sb.write.valid          := state === StState.WRITE_SB && !io.fu.flush
  io.sb.write.bits.sq_idx    := uopReg.sq_idx
  io.sb.write.bits.rob_tag   := uopReg.rob_tag
  io.sb.write.bits.addr      := alignedAddr
  io.sb.write.bits.data      := storeData
  io.sb.write.bits.mask      := storeMask
  io.sb.write.bits.cacheable := pmaResult.cacheable

  private val resp = Wire(new FuResp)

  resp.result       := 0.U
  resp.rd           := 0.U
  resp.pc           := uopReg.pc
  resp.instr        := uopReg.instr
  resp.rob_tag      := uopReg.rob_tag
  resp.trap_req     := false.B
  resp.trap_target  := 0.U
  resp.trap_ret     := false.B
  resp.trap_ret_tgt := 0.U

  io.fu.resp.valid := state === StState.DONE && !io.fu.flush
  io.fu.resp.bits  := resp

  when(io.fu.flush) {
    state := StState.IDLE
  }.otherwise {
    switch(state) {
      is(StState.IDLE) {}

      is(StState.WRITE_SB) {
        state := StState.DONE
      }

      is(StState.DONE) {
        when(io.fu.resp.fire) {
          state := StState.IDLE
        }
      }
    }

    when(acceptFire) {
      uopReg := io.fu.req.bits
      state  := StState.WRITE_SB
    }
  }
}
