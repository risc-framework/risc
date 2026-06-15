package arch.core.st

import arch.core.exception.ExceptionCsrReq
import arch.core.fupool.{ FuReq, FuResp }
import arch.core.pma.PmaModeFactory
import arch.core.sb.StoreWriteBundle
import arch.configs._
import vutils.fsm.ElasticGraphSyntax
import vutils.graph.{ Node, NodeConfig, NodeSelector }
import chisel3._
import chisel3.util.Decoupled

object StPipeNode extends ChiselEnum {
  val WRITE_SB, RESP = Value
}

private class StPipeEntry(implicit p: Parameters) extends Bundle {
  val store = new StoreWriteBundle
  val resp  = new FuResp
}

class St(implicit p: Parameters) extends Node[Parameters]("st") with ElasticGraphSyntax {
  override protected def cfg: NodeConfig = NodeConfig(
    selector = NodeSelector(
      StDims.ISA -> p(ISA).name
    )
  )

  val fuReq      = inD[FuReq]
  val fuResp     = outD[FuResp]
  val flush      = in[ExceptionCsrReq]
  val storeWrite = outD[StoreWriteBundle]

  private val isaImpl = StIsaFactory.select(cfg)
  private val pma     = PmaModeFactory.getOrThrow("default")

  private def emptyEntry: StPipeEntry = 0.U.asTypeOf(new StPipeEntry)

  private val acceptCtrl        = isaImpl.decode(fuReq.in.bits.uop)
  private val acceptAddr        = fuReq.in.bits.rs1_data + fuReq.in.bits.imm
  private val acceptAlignedAddr = isaImpl.alignedAddr(acceptAddr)
  private val acceptStoreData   =
    isaImpl.alignedStoreData(acceptCtrl, acceptAddr, fuReq.in.bits.rs2_data)
  private val acceptStoreMask   = isaImpl.shiftedStoreMask(acceptCtrl, acceptAddr)
  private val acceptPmaResult   = pma.check(acceptAddr)

  private val acceptEntry = WireDefault(emptyEntry)

  acceptEntry.store.sq_idx    := fuReq.in.bits.sq_idx
  acceptEntry.store.rob_tag   := fuReq.in.bits.rob_tag
  acceptEntry.store.addr      := acceptAlignedAddr
  acceptEntry.store.data      := acceptStoreData
  acceptEntry.store.mask      := acceptStoreMask
  acceptEntry.store.cacheable := acceptPmaResult.cacheable

  acceptEntry.resp.result       := 0.U
  acceptEntry.resp.rd           := 0.U
  acceptEntry.resp.pc           := fuReq.in.bits.pc
  acceptEntry.resp.instr        := fuReq.in.bits.instr
  acceptEntry.resp.rob_tag      := fuReq.in.bits.rob_tag
  acceptEntry.resp.trap_req     := false.B
  acceptEntry.resp.trap_kind    := 0.U
  acceptEntry.resp.trap_target  := 0.U
  acceptEntry.resp.trap_ret     := false.B
  acceptEntry.resp.trap_ret_tgt := 0.U

  private val acceptIn = Wire(Decoupled(new StPipeEntry))
  private val respOut  = Wire(Decoupled(new StPipeEntry))

  acceptIn.valid := fuReq.in.valid && !flush.in.flush
  acceptIn.bits  := acceptEntry
  fuReq.in.ready := acceptIn.ready && !flush.in.flush

  respOut.ready := fuResp.out.ready && !flush.in.flush

  private val pipe = elastic(new StPipeEntry, StPipeNode.WRITE_SB, clear = flush.in.flush) { g =>
    import g._

    val WRITE_SB = stage(StPipeNode.WRITE_SB)
    val RESP     = stage(StPipeNode.RESP)

    source(acceptIn, WRITE_SB)
    connect(WRITE_SB, RESP, trigger = storeWrite.out.ready)
    sink(RESP, respOut)
  }

  storeWrite.out.valid := pipe(StPipeNode.WRITE_SB).valid && pipe(
    StPipeNode.RESP
  ).ready && !flush.in.flush
  storeWrite.out.bits  := pipe(StPipeNode.WRITE_SB).bits.store

  fuResp.out.valid := respOut.valid && !flush.in.flush
  fuResp.out.bits  := respOut.bits.resp
}
