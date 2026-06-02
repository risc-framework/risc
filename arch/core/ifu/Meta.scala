package arch.core.ifu

import arch.core.bpu.BpuFetchIO
import vutils.graph.{ NodePort, NodeType }

object IfuMeta {
  val Type     = NodeType("ifu")
  val MEM      = NodePort[IfuIO, IfuMemIO]("mem", _.mem)
  val BPU      = NodePort[IfuIO, BpuFetchIO]("bpu", _.bpu)
  val REDIRECT = NodePort[IfuIO, IfuRedirectIO]("redirect", _.redirect)
  val DISPATCH = NodePort[IfuIO, IfuDispatchIO]("dispatch", _.dispatch)
}
