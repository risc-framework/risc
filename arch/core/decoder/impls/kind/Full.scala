package arch.core.decoder.impls.kind

import arch.configs._
import arch.core.decoder._
import vutils.graph.{ NodeRegistry, RegisteredNodeUtils }
import chisel3._

object FullDecoderKind extends RegisteredNodeUtils[DecoderKindImpl] {
  override def utils: DecoderKindImpl = new DecoderKindImpl {
    override def value: String = "full"

    override def decode(isa: DecoderIsaImpl, instr: UInt)(implicit p: Parameters): DecodedOutput = {
      val sigs    = Wire(new DecodedOutput)
      val decoder = DecodeLogic(instr, isa.default, isa.table)

      sigs.legal          := decoder(0).asBool
      sigs.regwrite       := decoder(1).asBool
      sigs.rs1_valid      := decoder(2).asBool
      sigs.rs2_valid      := decoder(3).asBool
      sigs.rd_valid       := decoder(4).asBool
      sigs.commit_barrier := decoder(5).asBool
      sigs.imm_type       := decoder(6)
      sigs.fu_type        := decoder(7)
      sigs.uop            := decoder(8)

      sigs
    }
  }

  override def registry: NodeRegistry[DecoderKindImpl] = DecoderKindFactory
}
