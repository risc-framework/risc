package arch.core.ifu

import arch.core.bpu.BpuFetchIO
import vutils.graph.{ NodePort, NodeType }
import chisel3._
import chisel3.util.DecoupledIO

object IfuMeta {
  val Type      = NodeType("ifu")
  val MEM       = NodePort[IfuIO, IfuMemIO]("mem", _.mem)
  val DECODE    = NodePort[IfuIO, Vec[DecoupledIO[IBufferEntry]]]("decode", _.decode)
  val EXCEPTION = NodePort[IfuIO, IfuExceptionIO]("exception", _.exception)
  val BPU       = NodePort[IfuIO, BpuFetchIO]("bpu", _.bpu)
  val DISPATCH  = NodePort[IfuIO, IfuDispatchIO]("dispatch", _.dispatch)
}
