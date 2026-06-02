package arch.core.memarb

import arch.configs._
import vcache.CachePortIO
import chisel3._

class MemoryArbiterLoadIO(implicit p: Parameters) extends Bundle {
  val mem  = Vec(p(NumLDs), Flipped(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams))))
  val mmio = Vec(p(NumLDs), Flipped(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams))))
}

class MemoryArbiterStoreIO(implicit p: Parameters) extends Bundle {
  val mem  = Flipped(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams)))
  val mmio = Flipped(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams)))
}

class MemoryArbiterOutIO(implicit p: Parameters) extends Bundle {
  val mem  = new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams))
  val mmio = new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams))
}
