package arch.node.memarb

import arch.core.fu.FunctionalUnitType
import arch.configs._
import vcache.CachePortIO
import chisel3._

class MemoryArbiterLoadIO(implicit p: Parameters) extends Bundle {
  private val n = p(FunctionalUnits).count(_.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD)
  val mem       = Vec(n, Flipped(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams))))
  val mmio      = Vec(n, Flipped(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams))))
}

class MemoryArbiterStoreIO(implicit p: Parameters) extends Bundle {
  val mem  = Flipped(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams)))
  val mmio = Flipped(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams)))
}

class MemoryArbiterOutIO(implicit p: Parameters) extends Bundle {
  val mem  = new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams))
  val mmio = new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams))
}

class MemoryArbiterIO(implicit p: Parameters) extends Bundle {
  val load  = new MemoryArbiterLoadIO
  val store = new MemoryArbiterStoreIO
  val out   = new MemoryArbiterOutIO
}
