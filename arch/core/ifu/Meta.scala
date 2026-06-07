package arch.core.ifu

import vcache.CacheReadOnlyPortIO
import vutils.graph.{ NodePort, NodeType }
import chisel3._

object IfuMeta {
  val Type      = NodeType("ifu")
  val ICACHE    = NodePort[IfuIO, CacheReadOnlyPortIO[Vec[UInt]]]("icache", _.icache)
  val DECODE    = NodePort[IfuIO, IfuDecodeIO]("decode", _.decode)
  val EXCEPTION = NodePort[IfuIO, IfuExceptionIO]("exception", _.exception)
  val BPU       = NodePort[IfuIO, IfuBpuIO]("bpu", _.bpu)
}
