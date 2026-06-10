package arch.core.fupool

import arch.configs._
import arch.core.alu.Alu
import arch.core.bru.Bru
import arch.core.csr.Csr
import arch.core.div.Div
import arch.core.exception.{ ExceptionFuPoolReq, ExceptionFuPoolResp }
import arch.core.interrupt.InterruptFuPoolResp
import arch.core.ld.Ld
import arch.core.memarb.{ MemoryArbiterCacheReq, MemoryArbiterCacheResp }
import arch.core.mult.Mult
import arch.core.rob.{ RobBruResolved, RobFuDone }
import arch.core.sb.{ StoreBufferStatus, StoreForwardReq, StoreForwardResp, StoreWriteBundle }
import arch.core.st.St
import chisel3._
import chisel3.util.DecoupledIO
import vutils.graph.Node

class FuPool(implicit p: Parameters) extends Node[Parameters]("fu_pool") {
  val cpu = in[FuPoolCpuReq]

  val exceptionReq  = in[ExceptionFuPoolReq]
  val exceptionResp = out[ExceptionFuPoolResp]
  val interruptResp = out[InterruptFuPoolResp]

  val schedulerReq  = inDVec[FuReq](p => p(NumFUs))
  val schedulerDone = outVVec[FuResp](p => p(NumFUs))

  val robDone     = outVVec[RobFuDone](p => p(NumFUs))
  val bruResolved = outVVec[RobBruResolved](p => p(NumBRUs))

  val loadMemReq   = outDVec[MemoryArbiterCacheReq](p => p(NumLDs))
  val loadMemResp  = inDVec[MemoryArbiterCacheResp](p => p(NumLDs))
  val loadMmioReq  = outDVec[MemoryArbiterCacheReq](p => p(NumLDs))
  val loadMmioResp = inDVec[MemoryArbiterCacheResp](p => p(NumLDs))

  val storeForwardReq   = outDVec[StoreForwardReq](p => p(NumLDs))
  val storeForwardResp  = inDVec[StoreForwardResp](p => p(NumLDs))
  val storeBufferStatus = in[StoreBufferStatus]
  val storeWrite        = outVVec[StoreWriteBundle](p => p(NumSTs))

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

  private def defaultOut[T <: Data](port: DecoupledIO[T]): Unit = {
    port.valid := false.B
    port.bits  := 0.U.asTypeOf(port.bits)
  }

  private def defaultIn[T <: Data](port: DecoupledIO[T]): Unit =
    port.ready := false.B

  private def forward[T <: Data](sink: DecoupledIO[T], source: DecoupledIO[T]): Unit = {
    sink.valid   := source.valid
    sink.bits    := source.bits
    source.ready := sink.ready
  }

  private def connectFu(
    fuReq: DecoupledIO[FuReq],
    fuResp: DecoupledIO[FuResp],
    fuFlush: FuFlushReq,
    idx: Int
  ): Unit = {
    fuFlush.flush := exceptionReq.in.flush

    forward(fuReq, schedulerReq.in.lanes(idx))

    fuResp.ready := true.B

    schedulerDone.out.lanes(idx).valid := fuResp.valid && !exceptionReq.in.flush
    schedulerDone.out.lanes(idx).bits  := fuResp.bits

    robDone.out.lanes(idx).valid             := fuResp.valid && !exceptionReq.in.flush
    robDone.out.lanes(idx).bits.rob_tag      := fuResp.bits.rob_tag
    robDone.out.lanes(idx).bits.result       := fuResp.bits.result
    robDone.out.lanes(idx).bits.trap_req     := fuResp.bits.trap_req
    robDone.out.lanes(idx).bits.trap_target  := fuResp.bits.trap_target
    robDone.out.lanes(idx).bits.trap_ret     := fuResp.bits.trap_ret
    robDone.out.lanes(idx).bits.trap_ret_tgt := fuResp.bits.trap_ret_tgt
  }

  require(
    p(FunctionalUnits).length == p(NumFUs),
    s"FuPool: FunctionalUnits length ${p(FunctionalUnits).length} != NumFUs ${p(NumFUs)}"
  )

  for (i <- 0 until p(NumFUs)) {
    schedulerReq.in.lanes(i).ready := false.B

    schedulerDone.out.lanes(i).valid := false.B
    schedulerDone.out.lanes(i).bits  := 0.U.asTypeOf(new FuResp)

    robDone.out.lanes(i).valid := false.B
    robDone.out.lanes(i).bits  := 0.U.asTypeOf(new RobFuDone)
  }

  for (i <- 0 until p(NumBRUs)) {
    bruResolved.out.lanes(i).valid := false.B
    bruResolved.out.lanes(i).bits  := 0.U.asTypeOf(new RobBruResolved)
  }

  for (i <- 0 until p(NumLDs)) {
    defaultOut(loadMemReq.out.lanes(i))
    defaultIn(loadMemResp.in.lanes(i))

    defaultOut(loadMmioReq.out.lanes(i))
    defaultIn(loadMmioResp.in.lanes(i))

    defaultOut(storeForwardReq.out.lanes(i))
    defaultIn(storeForwardResp.in.lanes(i))
  }

  for (i <- 0 until p(NumSTs)) {
    storeWrite.out.lanes(i).valid := false.B
    storeWrite.out.lanes(i).bits  := 0.U.asTypeOf(new StoreWriteBundle)
  }

  interruptResp.out.view     := 0.U.asTypeOf(interruptResp.out.view)
  exceptionResp.out.csr_busy := false.B

  private val units = p(FunctionalUnits).zipWithIndex.map { case (desc, idx) =>
    subnode(build(desc)) -> idx
  }

  private var ldIdx  = 0
  private var stIdx  = 0
  private var bruIdx = 0

  for ((unit, fuIdx) <- units)
    unit match {
      case alu: Alu =>
        connectFu(alu.fuReq.in, alu.fuResp.out, alu.flush.in, fuIdx)

      case mult: Mult =>
        connectFu(mult.fuReq.in, mult.fuResp.out, mult.flush.in, fuIdx)

      case div: Div =>
        connectFu(div.fuReq.in, div.fuResp.out, div.flush.in, fuIdx)

      case ld: Ld =>
        connectFu(ld.fuReq.in, ld.fuResp.out, ld.flush.in, fuIdx)

        forward(loadMemReq.out.lanes(ldIdx), ld.memReq.out)
        forward(ld.memResp.in, loadMemResp.in.lanes(ldIdx))

        forward(loadMmioReq.out.lanes(ldIdx), ld.mmioReq.out)
        forward(ld.mmioResp.in, loadMmioResp.in.lanes(ldIdx))

        forward(storeForwardReq.out.lanes(ldIdx), ld.fwdReq.out)
        forward(ld.fwdResp.in, storeForwardResp.in.lanes(ldIdx))

        ld.sbStatus.in := storeBufferStatus.in

        ldIdx += 1

      case st: St =>
        connectFu(st.fuReq.in, st.fuResp.out, st.flush.in, fuIdx)

        storeWrite.out.lanes(stIdx).valid := st.storeWrite.out.valid
        storeWrite.out.lanes(stIdx).bits  := st.storeWrite.out.bits

        stIdx += 1

      case bru: Bru =>
        connectFu(bru.fuReq.in, bru.fuResp.out, bru.flush.in, fuIdx)

        bruResolved.out.lanes(bruIdx).valid            := bru.resolved.out.valid && !exceptionReq.in.flush
        bruResolved.out.lanes(bruIdx).bits.rob_tag     := bru.resolved.out.bits.rob_tag
        bruResolved.out.lanes(bruIdx).bits.taken       := bru.resolved.out.bits.taken
        bruResolved.out.lanes(bruIdx).bits.target      := bru.resolved.out.bits.target
        bruResolved.out.lanes(bruIdx).bits.fallthrough := bru.resolved.out.bits.fallthrough

        bruIdx += 1

      case csr: Csr =>
        connectFu(csr.fuReq.in, csr.fuResp.out, csr.flush.in, fuIdx)

        csr.ctrlReq.in.cycle       := cpu.in.cycle
        csr.ctrlReq.in.instret     := cpu.in.instret
        csr.ctrlReq.in.irq         := cpu.in.irq
        csr.ctrlReq.in.arch_pc     := exceptionReq.in.arch_pc
        csr.ctrlReq.in.trap_update := exceptionReq.in.trap_update

        interruptResp.out.view     := csr.ctrlResp.out.view
        exceptionResp.out.csr_busy := csr.ctrlResp.out.busy

      case _ =>
    }
}
