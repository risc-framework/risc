package arch.core.cpu

import arch.core.csr.InterruptLines
import vcache.CachePortIO
import vutils.graph.{ NodePort, NodeType }
import chisel3._

object CpuMeta {
  val Type  = NodeType("cpu")
  val IMEM  = NodePort[CpuIO, CachePortIO[Vec[UInt]]]("imem", _.imem)
  val DMEM  = NodePort[CpuIO, CachePortIO[UInt]]("dmem", _.dmem)
  val MMIO  = NodePort[CpuIO, CachePortIO[UInt]]("mmio", _.mmio)
  val IRQ   = NodePort[CpuIO, InterruptLines]("irq", _.irq)
  val DEBUG = NodePort[CpuIO, DebugIO]("debug", _.debug)
}
