package arch.node.decoder

import arch.configs._
import vutils.graph.{ Node, NodeConfig, NodeSelector, NodeType }
import chisel3._

class DecoderIO(implicit p: Parameters) extends Bundle {
  val decode = new DecoderDecodeIO
}

class Decoder(implicit p: Parameters) extends Node(new DecoderIO) {
  private val cfg = NodeConfig(
    selector = NodeSelector(
      DecoderDims.ISA  -> p(ISA).name,
      DecoderDims.KIND -> p(DecoderKind)
    )
  )

  override def nodeType: NodeType  = DecoderMeta.Type
  override def desiredName: String = s"decoder_${cfg.selector.canonicalName}"

  private val isaImpl  = DecoderIsaFactory.select(cfg)
  private val kindImpl = DecoderKindFactory.select(cfg)

  for (w <- 0 until p(IssueWidth))
    io.decode.out(w) := kindImpl.decode(isaImpl, io.decode.instr(w))
}
