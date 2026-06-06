package arch.core.decode

import arch.core.dispatch.DispatchDecodeIO
import arch.core.ifu.IfuDecodeIO
import arch.configs._
import chisel3._
import vutils.graph.{ Node, NodeConfig, NodeSelector, NodeType }

class DecodeIO(implicit p: Parameters) extends Bundle {
  val ifu      = Flipped(new IfuDecodeIO)
  val dispatch = Flipped(new DispatchDecodeIO)
}

class Decode(implicit p: Parameters) extends Node(new DecodeIO) {
  private val cfg = NodeConfig(
    selector = NodeSelector(
      DecodeDims.ISA  -> p(ISA).name,
      DecodeDims.KIND -> p(DecodeKind)
    )
  )

  override def nodeType: NodeType  = DecodeMeta.Type
  override def desiredName: String = s"decode_${cfg.selector.canonicalName}"

  private val isaImpl  = DecodeIsaFactory.select(cfg)
  private val kindImpl = DecodeKindFactory.select(cfg)

  for (w <- 0 until p(IssueWidth)) {
    val packet = Wire(new DecodePacket)

    packet.pc               := io.ifu.lanes(w).bits.pc
    packet.instr            := io.ifu.lanes(w).bits.instr
    packet.bpu_pred_taken   := io.ifu.lanes(w).bits.bpu_pred_taken
    packet.bpu_pred_target  := io.ifu.lanes(w).bits.bpu_pred_target
    packet.bpu_pht_index    := io.ifu.lanes(w).bits.bpu_pht_index
    packet.bpu_ghr_snapshot := io.ifu.lanes(w).bits.bpu_ghr_snapshot

    io.dispatch.lanes(w).valid := io.ifu.lanes(w).valid
    io.ifu.lanes(w).ready      := io.dispatch.lanes(w).ready
    io.dispatch.lanes(w).bits  := kindImpl.decode(isaImpl, packet)
  }
}
