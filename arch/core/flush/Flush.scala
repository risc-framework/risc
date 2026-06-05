package arch.core.flush

import arch.configs._
import vutils.graph.{ Node, NodeType }
import chisel3._
import chisel3.util.Mux1H

class FlushIO extends Bundle {
  val rob       = new FlushRobIO
  val exception = new FlushExceptionIO
}

class Flush extends Node(new FlushIO) {
  override def nodeType: NodeType  = FlushMeta.Type
  override def desiredName: String = "flush"

  val flush  = io.rob.flushes.reduce(_ || _)
  val target = Mux1H(io.rob.flushes, io.rob.targets)

  io.exception.redirect := flush
  io.exception.target   := target
}
