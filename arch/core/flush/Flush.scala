package arch.core.flush

import arch.configs._
import arch.core.exception.ExceptionRequest
import vutils.graph.Node
import chisel3._
import chisel3.util.PriorityEncoder

class Flush(implicit p: Parameters) extends Node[Parameters]("flush") {
  val rob       = in[FlushRobReq]
  val exception = out[ExceptionRequest]

  private val flushValid  = rob.in.flushes.asUInt.orR
  private val flushSlot   = PriorityEncoder(rob.in.flushes.asUInt)
  private val flushTarget = rob.in.targets(flushSlot)

  exception.out := 0.U.asTypeOf(new ExceptionRequest)

  exception.out.valid             := flushValid
  exception.out.target            := Mux(flushValid, flushTarget, 0.U)
  exception.out.cause             := 0.U
  exception.out.write_csr         := false.B
  exception.out.requires_csr_idle := false.B
}
