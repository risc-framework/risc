package arch.core.memarb

import vcache.CachePortIO
import vutils.graph.{ NodePort, NodeType }
import chisel3._

object MemoryArbiterMeta {
  val Type    = NodeType("memory_arbiter")
  val FU_POOL = NodePort[MemoryArbiterIO, MemoryArbiterFuPoolIO]("fu_pool", _.fu_pool)
  val SB      = NodePort[MemoryArbiterIO, MemoryArbiterSbIO]("sb", _.sb)
  val DCACHE  = NodePort[MemoryArbiterIO, CachePortIO[UInt]]("dcache", _.dcache)
  val MMIO    = NodePort[MemoryArbiterIO, CachePortIO[UInt]]("mmio", _.mmio)
}
