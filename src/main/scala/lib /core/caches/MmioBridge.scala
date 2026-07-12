package arch.core.caches

import arch.configs._
import arch.core.cpu.{ CpuDmemReq, CpuDmemResp }
import arch.core.memarb.{ MemoryArbiterCacheReq, MemoryArbiterCacheResp }
import vutils.graph.Node

class MmioBridge(implicit p: Parameters) extends Node[Parameters]("mmio_bridge") {
  val upperReq  = inD[MemoryArbiterCacheReq]
  val upperResp = outD[MemoryArbiterCacheResp]

  val lowerReq  = outD[CpuDmemReq]
  val lowerResp = inD[CpuDmemResp]

  lowerReq.out.valid := upperReq.in.valid
  lowerReq.out.bits  := upperReq.in.bits.asTypeOf(lowerReq.out.bits)
  upperReq.in.ready  := lowerReq.out.ready

  upperResp.out.valid := lowerResp.in.valid
  upperResp.out.bits  := lowerResp.in.bits.asTypeOf(upperResp.out.bits)
  lowerResp.in.ready  := upperResp.out.ready
}
