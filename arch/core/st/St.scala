package arch.core.st

import arch.core.pma.PmaModeFactory
import arch.core.fupool.{ FuResp, FuReq, FuFlushReq }
import arch.core.sb.StoreWriteBundle
import arch.configs._
import vutils.graph.{ Node, NodeConfig, NodeSelector }
import chisel3._
import chisel3.util.{ is, switch }

object StState extends ChiselEnum {
  val IDLE, WRITE_SB, DONE = Value
}

class St(implicit p: Parameters) extends Node[Parameters]("st") {
  override protected def cfg: NodeConfig = NodeConfig(
    selector = NodeSelector(
      StDims.ISA -> p(ISA).name
    )
  )

  val fuReq      = inD[FuReq]
  val fuResp     = outD[FuResp]
  val flush      = in[FuFlushReq]
  val storeWrite = outV[StoreWriteBundle]

  private val isaImpl = StIsaFactory.select(cfg)
  private val pma     = PmaModeFactory.getOrThrow("default")
  private val state   = RegInit(StState.IDLE)
  private val uopReg  = Reg(new FuReq)

  private val ctrl        = isaImpl.decodeStore(uopReg.uop)
  private val addr        = uopReg.rs1_data + uopReg.imm
  private val alignedAddr = isaImpl.alignedAddr(addr)
  private val storeData   = isaImpl.alignedStoreData(ctrl, addr, uopReg.rs2_data)
  private val storeMask   = isaImpl.shiftedStoreMask(ctrl, addr)
  private val pmaResult   = pma.check(addr)

  fuReq.in.ready := !flush.in.flush && (state === StState.IDLE || (state === StState.DONE && fuResp.out.ready))

  private val acceptFire = fuReq.in.fire && !flush.in.flush

  storeWrite.out.valid          := state === StState.WRITE_SB && !flush.in.flush
  storeWrite.out.bits.sq_idx    := uopReg.sq_idx
  storeWrite.out.bits.rob_tag   := uopReg.rob_tag
  storeWrite.out.bits.addr      := alignedAddr
  storeWrite.out.bits.data      := storeData
  storeWrite.out.bits.mask      := storeMask
  storeWrite.out.bits.cacheable := pmaResult.cacheable

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

  fuResp.out.valid := state === StState.DONE && !flush.in.flush
  fuResp.out.bits  := resp

  when(flush.in.flush) {
    state := StState.IDLE
  }.otherwise {
    switch(state) {
      is(StState.IDLE) {}

      is(StState.WRITE_SB) {
        state := StState.DONE
      }

      is(StState.DONE) {
        when(fuResp.out.fire) {
          state := StState.IDLE
        }
      }
    }

    when(acceptFire) {
      uopReg := fuReq.in.bits
      state  := StState.WRITE_SB
    }
  }
}
