package arch.core.ifu

import vutils.graph.{ NodePort, NodeType }

object IfuMeta {
  val Type      = NodeType("ifu")
  val ICACHE    = NodePort[IfuIO, IfuICacheIO]("icache", _.icache)
  val DECODE    = NodePort[IfuIO, IfuDecodeIO]("decode", _.decode)
  val EXCEPTION = NodePort[IfuIO, IfuExceptionIO]("exception", _.exception)
  val BPU       = NodePort[IfuIO, IfuBpuIO]("bpu", _.bpu)
}
