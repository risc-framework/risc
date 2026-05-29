package arch.node.ld

import arch.node.fupool.FuIO
import arch.node.sb.StoreForwardIO
import arch.configs._
import vcache.CachePortIO
import chisel3._

class LdMemIO(implicit p: Parameters) extends Bundle {
  val mem  = new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams))
  val mmio = new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams))
}

class LdSbIO(implicit p: Parameters) extends Bundle {
  val sb_fwd = Flipped(new StoreForwardIO)
}

class LdIO(implicit p: Parameters) extends Bundle {
  val fu  = new FuIO
  val mem = new LdMemIO
  val sb  = new LdSbIO
}
