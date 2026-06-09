package arch.core.csr

import arch.configs._
import arch.core.fupool.{ FuFlushReq, FuReq, FuResp }
import chisel3._
import vutils.graph.{ Node, NodeConfig, NodeSelector }

class Csr(implicit p: Parameters) extends Node[Parameters]("csr") {
  override protected def cfg: NodeConfig = NodeConfig(
    selector = NodeSelector(
      CsrDims.ISA -> p(ISA).name
    )
  )

  val fuReq  = inD[FuReq]
  val fuResp = outD[FuResp]
  val flush  = in[FuFlushReq]

  val ctrlReq  = in[CsrCtrlReq]
  val ctrlResp = out[CsrCtrlResp]

  private val isaImpl = CsrIsaFactory.select(cfg)

  private val busy   = RegInit(false.B)
  private val uopReg = RegInit(0.U.asTypeOf(new FuReq))

  private val csrTable = isaImpl.table
  private val csrRegs  = csrTable.map { case (reg, _) =>
    val r = RegInit(reg.initValue.U(p(XLen).W))
    r.suggestName(reg.name)
    r
  }

  private val addrMap    = csrTable.map { case (reg, _) => reg.addr.U(isaImpl.addrWidth.W) }
  private val regNameMap = csrTable.map(_._1.name).zip(csrRegs).toMap

  private val extraMap = Map(
    "cycle"     -> ctrlReq.in.cycle,
    "instret"   -> ctrlReq.in.instret,
    "timer_irq" -> ctrlReq.in.irq.timer_irq.asUInt,
    "soft_irq"  -> ctrlReq.in.irq.soft_irq.asUInt,
    "ext_irq"   -> ctrlReq.in.irq.ext_irq.asUInt
  )

  fuReq.in.ready    := !flush.in.flush && (!busy || fuResp.out.fire)
  fuResp.out.valid  := busy && !flush.in.flush
  ctrlResp.out.busy := busy

  when(flush.in.flush) {
    busy := false.B
  }.elsewhen(fuReq.in.fire) {
    busy   := true.B
    uopReg := fuReq.in.bits
  }.elsewhen(fuResp.out.fire) {
    busy := false.B
  }

  private val activeInstr = Mux(busy, uopReg.instr, 0.U(p(ILen).W))
  private val activeUop   = Mux(busy, uopReg.uop, 0.U.asTypeOf(uopReg.uop))
  private val ctrl        = isaImpl.decode(activeUop)

  private val trapRet       = busy && isaImpl.isTrapReturn(activeInstr, activeUop)
  private val syncException = busy && isaImpl.hasSyncException(activeInstr, activeUop)
  private val syncCause     = isaImpl.syncExceptionCause(activeInstr, activeUop)

  private val localTrapUpdate = Wire(new CsrTrapUpdate)

  localTrapUpdate.valid := syncException
  localTrapUpdate.pc    := uopReg.pc
  localTrapUpdate.cause := syncCause

  private val trapUpdate = Wire(new CsrTrapUpdate)

  trapUpdate.valid := localTrapUpdate.valid || ctrlReq.in.trap_update.valid
  trapUpdate.pc    := Mux(localTrapUpdate.valid, localTrapUpdate.pc, ctrlReq.in.trap_update.pc)
  trapUpdate.cause := Mux(
    localTrapUpdate.valid,
    localTrapUpdate.cause,
    ctrlReq.in.trap_update.cause
  )

  private val csrAddr  = isaImpl.getAddr(activeInstr)
  private val hits     = addrMap.map(_ === csrAddr)
  private val hitAny   = hits.reduce(_ || _)
  private val wrHits   = csrTable.zip(hits).map { case ((reg, _), hit) => hit && reg.writable.B }
  private val wrHitAny = wrHits.reduce(_ || _)
  private val wrAllow  = hitAny && wrHitAny && !ctrl.is_sys
  private val srcData  = Mux(ctrl.is_imm, uopReg.imm, uopReg.rs1_data)

  private val trapUpdates = isaImpl.trapEntryUpdates(regNameMap, trapUpdate.pc, trapUpdate.cause)
  private val retUpdates  = isaImpl.trapReturnUpdates(regNameMap)

  ctrlResp.out.view := isaImpl.view(regNameMap, extraMap)

  for (((reg, behavior), i) <- csrTable.zipWithIndex) {
    val trapHit = trapUpdate.valid && trapUpdates.contains(reg.name).B
    val trapVal = trapUpdates.getOrElse(reg.name, 0.U(p(XLen).W))
    val retHit  = busy && trapRet && retUpdates.contains(reg.name).B
    val retVal  = retUpdates.getOrElse(reg.name, 0.U(p(XLen).W))

    behavior match {
      case AlwaysUpdate(fn) =>
        csrRegs(i) := fn(extraMap)

      case ConditionalUpdate(fn) =>
        csrRegs(i) := fn(extraMap)

        when(trapHit) {
          csrRegs(i) := trapVal
        }.elsewhen(retHit) {
          csrRegs(i) := retVal
        }.elsewhen(busy && wrAllow && hits(i) && reg.writable.B) {
          csrRegs(i) := isaImpl.fn(ctrl.op, csrRegs(i), srcData)
        }

      case NormalUpdate =>
        when(trapHit) {
          csrRegs(i) := trapVal
        }.elsewhen(retHit) {
          csrRegs(i) := retVal
        }.elsewhen(busy && wrAllow && hits(i) && reg.writable.B) {
          csrRegs(i) := isaImpl.fn(ctrl.op, csrRegs(i), srcData)
        }
    }
  }

  private val readMasked =
    hits.zip(csrRegs).map { case (hit, data) => Mux(hit, data, 0.U(p(XLen).W)) }

  private val rdData    = Mux(busy && !ctrl.is_sys, readMasked.reduce(_ | _), 0.U(p(XLen).W))
  private val retTarget = isaImpl.trapReturnTarget(regNameMap)
  private val resp      = WireDefault(0.U.asTypeOf(new FuResp))

  resp.result       := rdData
  resp.rd           := uopReg.rd
  resp.pc           := uopReg.pc
  resp.instr        := uopReg.instr
  resp.rob_tag      := uopReg.rob_tag
  resp.trap_req     := syncException
  resp.trap_target  := isaImpl.trapTarget(ctrlResp.out.view)
  resp.trap_ret     := trapRet
  resp.trap_ret_tgt := retTarget

  fuResp.out.bits := resp
}
