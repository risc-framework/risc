package arch.core.fupool

import arch.configs._
import arch.core.alu.Alu
import arch.core.bru.{ Bru, BruResolveBundle }
import arch.core.csr.{ Csr, InterruptLines }
import arch.core.div.Div
import arch.core.exception.{ ExceptionAsyncReq, ExceptionTrapUpdate }
import arch.core.ld.Ld
import arch.core.memarb.{ MemoryArbiterCacheReq, MemoryArbiterCacheResp }
import arch.core.mult.Mult
import arch.core.sb.{ StoreBufferStatus, StoreForwardReq, StoreForwardResp, StoreWriteBundle }
import arch.core.st.St
import vutils.graph.Node
import chisel3._

class FuPool(implicit p: Parameters) extends Node[Parameters]("fu_pool") {
  val cpu        = in[FuPoolCpuReq]
  val flush      = in[Bool]
  val irq        = in[InterruptLines]
  val trapUpdate = in[ExceptionTrapUpdate]
  val async      = out[ExceptionAsyncReq]
  val csrBusy    = out[Bool]

  val schedulerReq  = inDVec[FuReq](p => p(NumFUs))
  val schedulerDone = outDVec[FuResp](p => p(NumFUs))
  val robDone       = outDVec[FuResp](p => p(NumFUs))

  val bruResolved = outVVec[BruResolveBundle](p => p(NumBRUs))

  val loadMemReq   = outDVec[MemoryArbiterCacheReq](p => p(NumLDs))
  val loadMemResp  = inDVec[MemoryArbiterCacheResp](p => p(NumLDs))
  val loadMmioReq  = outDVec[MemoryArbiterCacheReq](p => p(NumLDs))
  val loadMmioResp = inDVec[MemoryArbiterCacheResp](p => p(NumLDs))

  val storeForwardReq   = outDVec[StoreForwardReq](p => p(NumLDs))
  val storeForwardResp  = inDVec[StoreForwardResp](p => p(NumLDs))
  val storeBufferStatus = in[StoreBufferStatus]
  val storeWrite        = outDVec[StoreWriteBundle](p => p(NumSTs))
  val debug             = out[FuPoolDebugInfo]

  private def build(desc: FunctionalUnitDescriptor): Node[Parameters] =
    desc.`type` match {
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ALU  => new Alu
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_MULT => new Mult
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_DIV  => new Div
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD   => new Ld
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST   => new St
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_BRU  => new Bru
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR  => new Csr
      case other                                        =>
        throw new UnsupportedOperationException(
          s"FuPool: unsupported FU type '${other.cppName}' for '${desc.name}'"
        )
    }

  private val fuDescs = p(FunctionalUnits)
  require(p(NumCSRs) <= 1, s"FuPool: at most one CSR FU is supported, got ${p(NumCSRs)}")

  private val units = fuDescs.zipWithIndex.map { case (desc, idx) =>
    subnode(build(desc)) -> idx
  }

  private val loadBusy        = Wire(Vec(p(NumLDs), Bool()))
  private val loadWaitMem     = Wire(Vec(p(NumLDs), Bool()))
  private val loadWaitForward = Wire(Vec(p(NumLDs), Bool()))

  loadBusy.foreach(_ := false.B)
  loadWaitMem.foreach(_ := false.B)
  loadWaitForward.foreach(_ := false.B)

  private var ldIdx  = 0
  private var stIdx  = 0
  private var bruIdx = 0

  for ((unit, fuIdx) <- units)
    unit match {
      case alu: Alu =>
        link(
          schedulerReq.lanes(fuIdx) -> alu.fuReq,
          flush                     -> alu.flush,
          alu.fuResp                -> schedulerDone.lanes(fuIdx),
          alu.fuResp                -> robDone.lanes(fuIdx)
        )

      case mult: Mult =>
        link(
          schedulerReq.lanes(fuIdx) -> mult.fuReq,
          flush                     -> mult.flush,
          mult.fuResp               -> schedulerDone.lanes(fuIdx),
          mult.fuResp               -> robDone.lanes(fuIdx)
        )

      case div: Div =>
        link(
          schedulerReq.lanes(fuIdx) -> div.fuReq,
          flush                     -> div.flush,
          div.fuResp                -> schedulerDone.lanes(fuIdx),
          div.fuResp                -> robDone.lanes(fuIdx)
        )

      case ld: Ld =>
        link(
          schedulerReq.lanes(fuIdx)     -> ld.fuReq,
          flush                         -> ld.flush,
          ld.fuResp                     -> schedulerDone.lanes(fuIdx),
          ld.fuResp                     -> robDone.lanes(fuIdx),
          ld.memReq                     -> loadMemReq.lanes(ldIdx),
          loadMemResp.lanes(ldIdx)      -> ld.memResp,
          ld.mmioReq                    -> loadMmioReq.lanes(ldIdx),
          loadMmioResp.lanes(ldIdx)     -> ld.mmioResp,
          ld.fwdReq                     -> storeForwardReq.lanes(ldIdx),
          storeForwardResp.lanes(ldIdx) -> ld.fwdResp,
          storeBufferStatus             -> ld.sbStatus
        )

        loadBusy(ldIdx)        := ld.debug.out.busy
        loadWaitMem(ldIdx)     := ld.debug.out.wait_mem
        loadWaitForward(ldIdx) := ld.debug.out.wait_forward

        ldIdx += 1

      case st: St =>
        link(
          schedulerReq.lanes(fuIdx) -> st.fuReq,
          flush                     -> st.flush,
          st.fuResp                 -> schedulerDone.lanes(fuIdx),
          st.fuResp                 -> robDone.lanes(fuIdx),
          st.storeWrite             -> storeWrite.lanes(stIdx)
        )

        stIdx += 1

      case bru: Bru =>
        link(
          schedulerReq.lanes(fuIdx) -> bru.fuReq,
          flush                     -> bru.flush,
          bru.fuResp                -> schedulerDone.lanes(fuIdx),
          bru.fuResp                -> robDone.lanes(fuIdx),
          bru.resolved              -> bruResolved.lanes(bruIdx)
        )

        bruIdx += 1

      case csr: Csr =>
        link(
          schedulerReq.lanes(fuIdx) -> csr.fuReq,
          flush                     -> csr.flush,
          irq                       -> csr.irq,
          trapUpdate                -> csr.trapUpdate,
          csr.fuResp                -> schedulerDone.lanes(fuIdx),
          csr.fuResp                -> robDone.lanes(fuIdx),
        )

        csr.ctrlReq.in.cycle   := cpu.in.cycle
        csr.ctrlReq.in.instret := cpu.in.instret

        async.out.valid  := csr.ctrlResp.out.ir.valid
        async.out.kind   := csr.ctrlResp.out.ir.kind
        async.out.target := csr.ctrlResp.out.ir.target
        csrBusy.out      := csr.ctrlResp.out.busy

      case _ =>
    }

  debug.out.load_wait_mem     := loadWaitMem.asUInt.orR
  debug.out.load_wait_forward := loadWaitForward.asUInt.orR
  debug.out.lsu_busy          := loadBusy.asUInt.orR
}
