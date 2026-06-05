package arch.core.decode

import arch.configs._
import chisel3._
import chisel3.util.Decoupled
import vutils.graph.{ Node, NodeConfig, NodeSelector, NodeType }

class DecodeIO(implicit p: Parameters) extends Bundle {
  val ifu = Flipped(Vec(p(IssueWidth), Decoupled(new DecodePacket)))
  val out = Vec(p(IssueWidth), Decoupled(new DecodedPacket))
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
    io.out(w).valid := io.ifu(w).valid
    io.ifu(w).ready := io.out(w).ready
    io.out(w).bits  := kindImpl.decode(isaImpl, io.ifu(w).bits)
  }
}
