package arch.node.st

import arch.configs._
import arch.core.pma.PmaChecker
import arch.node.fupool.{ FuIO, FuResp }
import arch.node.imm.ImmIsaFactory
import arch.node.uop.MicroOp
import vutils.graph.{ Node, NodeType, NodeConfig, NodeSelector }
import chisel3._
import chisel3.util.{ Valid, is, log2Ceil, switch }

class StoreWriteBundle(implicit p: Parameters) extends Bundle {
  val sq_idx    = UInt(log2Ceil(p(StoreBufferSize)).W)
  val rob_tag   = UInt(p(RobTagWidth).W)
  val addr      = UInt(p(XLen).W)
  val data      = UInt(p(XLen).W)
  val mask      = UInt(p(BytesPerWord).W)
  val cacheable = Bool()
}

class StSbWriteIO(implicit p: Parameters) extends Bundle {
  val sq_idx = Input(UInt(log2Ceil(p(StoreBufferSize)).W))
  val write  = Output(Valid(new StoreWriteBundle))
}

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

  private val isaImpl  = StIsaFactory.select(cfg)
  private val imm      = ImmIsaFactory.select(p(ISA).name)
  private val state    = RegInit(StState.IDLE)
  private val uopReg   = Reg(new MicroOp)
  private val sqIdxReg = RegInit(0.U(log2Ceil(p(StoreBufferSize)).W))

  private val ctrl                    = isaImpl.decodeStore(uopReg.uop)
  private val addr                    = uopReg.rs1_data + imm.gen(uopReg.instr, uopReg.imm_type)
  private val alignedAddr             = isaImpl.alignedAddr(addr)
  private val storeData               = isaImpl.alignedStoreData(ctrl, addr, uopReg.rs2_data)
  private val storeMask               = isaImpl.shiftedStoreMask(ctrl, addr)
  private val (_, _, _, pmaCacheable) = PmaChecker(addr)

  io.fu.req.ready := !io.fu.flush && (state === StState.IDLE || (state === StState.DONE && io.fu.resp.ready))

  private val acceptFire = io.fu.req.fire && !io.fu.flush

  io.sb.write.valid          := state === StState.WRITE_SB
  io.sb.write.bits.sq_idx    := sqIdxReg
  io.sb.write.bits.rob_tag   := uopReg.rob_tag
  io.sb.write.bits.addr      := alignedAddr
  io.sb.write.bits.data      := storeData
  io.sb.write.bits.mask      := storeMask
  io.sb.write.bits.cacheable := pmaCacheable

  private val resp = Wire(new FuResp)

  resp.result  := 0.U
  resp.rd      := 0.U
  resp.pc      := uopReg.pc
  resp.instr   := uopReg.instr
  resp.rob_tag := uopReg.rob_tag

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
      uopReg   := io.fu.req.bits
      sqIdxReg := io.sb.sq_idx
      state    := StState.WRITE_SB
    }
  }
}
