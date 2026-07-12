package arch.core.memarb

import arch.configs._
import chisel3._
import vcache.{ CacheReq, CacheResp }

class MemoryArbiterCacheReq(implicit p: Parameters)
    extends CacheReq(UInt(p(XLen).W), p(L1DCacheParams))

class MemoryArbiterCacheResp(implicit p: Parameters)
    extends CacheResp(UInt(p(XLen).W), p(L1DCacheParams))

class MemoryArbiterRoutedReq(targetWidth: Int)(implicit p: Parameters) extends Bundle {
  val target = UInt(targetWidth.W)
  val req    = new MemoryArbiterCacheReq
}
