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

  private def buildEntry(req: FuReq): StPipeEntry = {
    val ctrl  = isaImpl.decode(req.uop)
    val addr  = req.rs1_data + req.imm
    val entry = WireDefault(emptyEntry)

    entry.store.sq_idx    := req.sq_idx
    entry.store.rob_tag   := req.rob_tag
    entry.store.addr      := isaImpl.alignedAddr(addr)
    entry.store.data      := isaImpl.alignedStoreData(ctrl, addr, req.rs2_data)
    entry.store.mask      := isaImpl.shiftedStoreMask(ctrl, addr)
    entry.store.cacheable := pma.check(addr).cacheable

    entry.resp.result      := 0.U
    entry.resp.rd          := 0.U
    entry.resp.pc          := req.pc
    entry.resp.instr       := req.instr
    entry.resp.rob_tag     := req.rob_tag
    entry.resp.trap_req    := false.B
    entry.resp.trap_kind   := 0.U
    entry.resp.trap_target := 0.U

    entry
  }

  private val acceptIn = Wire(Decoupled(new StPipeEntry))

  acceptIn.valid := fuReq.in.valid && !flush.in.flush
  acceptIn.bits  := buildEntry(fuReq.in.bits)
  fuReq.in.ready := acceptIn.ready && !flush.in.flush

  elastic(new StPipeEntry, StPipeNode.WRITE_SB, clear = flush.in.flush) { g =>
    import g._

    val WRITE_SB = stage(StPipeNode.WRITE_SB)
    val RESP     = stage(StPipeNode.RESP)

    source(acceptIn, WRITE_SB)

    request(WRITE_SB, storeWrite.out, RESP) { store =>
      store := WRITE_SB.bits.store
    }

    sinkMap(RESP, fuResp.out) { resp =>
      resp := RESP.bits.resp
    }
  }
}
