package arch.core.decoder

import arch.core.imm.ImmUtilsFactory
import arch.configs._
import chisel3._

class DecodedOutput(implicit p: Parameters) extends Bundle {
  private val imm_utils = ImmUtilsFactory.getOrThrow(p(ISA).name)

  val legal    = Bool()
  val regwrite = Bool()
  val imm_type = UInt(imm_utils.immTypeWidth.W)

  val rs1_valid = Bool()
  val rs2_valid = Bool()
  val rd_valid  = Bool()

  val commit_barrier = Bool()

  val alu   = Bool()
  val mult  = Bool()
  val div   = Bool()
  val load  = Bool()
  val store = Bool()
  val bru   = Bool()
  val csr   = Bool()

  val uop = UInt(p(MicroOpWidth).W)
}

class Decoder(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_decoder"

  val utils = DecoderUtilsFactory.getOrThrow(p(ISA).name)

  val instr   = IO(Input(UInt(p(ILen).W)))
  val decoded = IO(Output(new DecodedOutput))

  decoded := utils.decode(instr)
}
