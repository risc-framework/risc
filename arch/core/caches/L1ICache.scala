package arch.core.caches

import arch.configs._
import arch.core.cpu.{ CpuImemReq, CpuImemResp }
import vcache.{ CacheReadReq, CacheResp }
import vcache.nonblocking.ReadOnlyNonBlockingCache
import vutils.graph.Node
import chisel3._

class L1ICache(implicit p: Parameters) extends Node[Parameters]("l1_icache") {
  val upperReq = inDWith[CacheReadReq] { _ =>
    new CacheReadReq(p(L1ICacheParams))
  }

  val upperResp = outDWith[CacheResp[Vec[UInt]]] { _ =>
    new CacheResp(Vec(p(IssueWidth), UInt(p(ILen).W)), p(L1ICacheParams))
  }

  val lowerReq  = outD[CpuImemReq]
  val lowerResp = inD[CpuImemResp]

  private val cache = Module(
    new ReadOnlyNonBlockingCache(Vec(p(IssueWidth), UInt(p(ILen).W)), p(L1ICacheParams))
  )

  cache.upper.req <> upperReq.in
  upperResp.out <> cache.upper.resp

  lowerReq.out <> cache.lower.req
  cache.lower.resp <> lowerResp.in
}
