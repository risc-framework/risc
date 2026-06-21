package arch.core.decode

import arch.core.ibuffer.IBufferEntry
import arch.configs._
import chisel3._
import vutils.graph.{ Node, NodeConfig, NodeSelector }

class Decode(implicit p: Parameters) extends Node[Parameters]("decode") {
  override protected def cfg: NodeConfig = NodeConfig(
    selector = NodeSelector(
      DecodeDims.ISA  -> p(ISA).name,
      DecodeDims.KIND -> p(DecodeKind)
    )
  )

  val issued   = inDVec[IBufferEntry](p => p(IssueWidth))
  val dispatch = outDVec[DecodedPacket](p => p(IssueWidth))

  private val isaImpl  = DecodeIsaFactory.select(cfg)
  private val kindImpl = DecodeKindFactory.select(cfg)

  for (w <- 0 until p(IssueWidth)) {
    val packet = Wire(new DecodePacket)

    packet.pc               := issued.in.lanes(w).bits.pc
    packet.instr            := issued.in.lanes(w).bits.instr
    packet.bpu_pred_taken   := issued.in.lanes(w).bits.bpu_pred_taken
    packet.bpu_pred_target  := issued.in.lanes(w).bits.bpu_pred_target
    packet.bpu_pht_index    := issued.in.lanes(w).bits.bpu_pht_index
    packet.bpu_ghr_snapshot := issued.in.lanes(w).bits.bpu_ghr_snapshot

    dispatch.out.lanes(w).valid := issued.in.lanes(w).valid
    issued.in.lanes(w).ready    := dispatch.out.lanes(w).ready
    dispatch.out.lanes(w).bits  := kindImpl.decode(isaImpl, packet)
  }
}
