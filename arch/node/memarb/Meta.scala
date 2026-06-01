package arch.node.memarb

import vutils.graph.{ NodePort, NodeType }

object MemoryArbiterMeta {
  val Type  = NodeType("memory_arbiter")
  val LOAD  = NodePort[MemoryArbiterIO, MemoryArbiterLoadIO]("load", _.load)
  val STORE = NodePort[MemoryArbiterIO, MemoryArbiterStoreIO]("store", _.store)
  val OUT   = NodePort[MemoryArbiterIO, MemoryArbiterOutIO]("out", _.out)
}
