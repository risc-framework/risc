package arch.core.caches

import arch.configs._
import arch.core.cpu.{ CpuDmemReq, CpuDmemResp }
import arch.core.memarb.{ MemoryArbiterCacheReq, MemoryArbiterCacheResp }
import vcache.nonblocking.NonBlockingCache
import vutils.graph.Node
import chisel3._
import chisel3.util.Queue

class L1DCache(implicit p: Parameters) extends Node[Parameters]("l1_dcache") {
  val upperReq  = inD[MemoryArbiterCacheReq]
  val upperResp = outD[MemoryArbiterCacheResp]

  val lowerReq  = outD[CpuDmemReq]
  val lowerResp = inD[CpuDmemResp]

  private val cache      = Module(new NonBlockingCache(UInt(p(XLen).W), p(L1DCacheParams)))
  private val lowerRespQ = Module(new Queue(new CpuDmemResp, 2, pipe = false, flow = false))

  cache.upper.req <> upperReq.in
  upperResp.out <> cache.upper.resp

  lowerReq.out <> cache.lower.req
  lowerRespQ.io.enq <> lowerResp.in
  cache.lower.resp <> lowerRespQ.io.deq
}
