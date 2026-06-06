package arch.core.memarb

import arch.configs._
import vcache.CachePortIO
import chisel3._

class MemoryArbiterFuPoolIO(implicit p: Parameters) extends Bundle {
  val load_mem  = Vec(p(NumLDs), Flipped(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams))))
  val load_mmio = Vec(p(NumLDs), Flipped(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams))))
}

class MemoryArbiterSbIO(implicit p: Parameters) extends Bundle {
  val mem  = Flipped(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams)))
  val mmio = Flipped(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams)))
}
