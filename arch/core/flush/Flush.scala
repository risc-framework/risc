package arch.core.flush

import arch.configs._
import vutils.graph.{ Node, NodeType }
import chisel3._
import chisel3.util.Mux1H

class FlushIO(implicit p: Parameters) extends Bundle {
  val rob       = new FlushRobIO
  val exception = new FlushExceptionIO
}

class Flush(implicit p: Parameters) extends Node(new FlushIO) {
  override def nodeType: NodeType  = FlushMeta.Type
  override def desiredName: String = "flush"

  private val flush  = io.rob.flushes.reduce(_ || _)
  private val target = Mux1H(io.rob.flushes, io.rob.targets)

  io.exception.request.valid             := flush
  io.exception.request.target            := target
  io.exception.request.cause             := 0.U
  io.exception.request.write_csr         := false.B
  io.exception.request.requires_csr_idle := false.B
}
