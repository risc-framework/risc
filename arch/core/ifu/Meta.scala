package arch.core.ifu

import arch.core.bpu.BpuFetchIO
import vutils.graph.{ NodePort, NodeType }

object IfuMeta {
  val Type      = NodeType("ifu")
  val MEM       = NodePort[IfuIO, IfuMemIO]("mem", _.mem)
  val EXCEPTION = NodePort[IfuIO, IfuExceptionIO]("exception", _.exception)
  val BPU       = NodePort[IfuIO, BpuFetchIO]("bpu", _.bpu)
  val DISPATCH  = NodePort[IfuIO, IfuDispatchIO]("dispatch", _.dispatch)
}
