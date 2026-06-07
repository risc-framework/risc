package arch.core.csr

import arch.configs._
import arch.core.fupool.{ FuIO, FuResp }
import arch.core.fupool.FuReq
import vutils.graph.{ Node, NodeConfig, NodeSelector, NodeType }
import chisel3._

class CsrIO(implicit p: Parameters) extends Bundle {
  val fu   = new FuIO
  val ctrl = new CsrCtrlIO
}

class Csr(implicit p: Parameters) extends Node(new CsrIO) {
  private val cfg = NodeConfig(
    selector = NodeSelector(
      CsrDims.ISA -> p(ISA).name
    )
  )

  override def nodeType: NodeType  = CsrMeta.Type
  override def desiredName: String = s"csr_${cfg.selector.canonicalName}"

  private val isaImpl = CsrIsaFactory.select(cfg)

  private val busy   = RegInit(false.B)
  private val uopReg = Reg(new FuReq)

  private val csrTable = isaImpl.table
  private val csrRegs  = csrTable.map { case (reg, _) =>
    val r = RegInit(reg.initValue.U(p(XLen).W))
    r.suggestName(reg.name)
    r
  }

  private val addrMap    = csrTable.map { case (reg, _) => reg.addr.U(isaImpl.addrWidth.W) }
  private val regNameMap = csrTable.map(_._1.name).zip(csrRegs).toMap

  private val extraMap = Map(
    "cycle"     -> io.ctrl.cycle,
    "instret"   -> io.ctrl.instret,
    "timer_irq" -> io.ctrl.irq.timer_irq.asUInt,
    "soft_irq"  -> io.ctrl.irq.soft_irq.asUInt,
    "ext_irq"   -> io.ctrl.irq.ext_irq.asUInt
  )

  io.fu.req.ready  := !io.fu.flush && (!busy || io.fu.resp.fire)
  io.fu.resp.valid := busy && !io.fu.flush
  io.ctrl.busy     := busy

  when(io.fu.flush) {
    busy := false.B
  }.elsewhen(io.fu.req.fire) {
    busy   := true.B
    uopReg := io.fu.req.bits
  }.elsewhen(io.fu.resp.fire) {
    busy := false.B
  }

  private val activeInstr = Mux(busy, uopReg.instr, 0.U(p(ILen).W))
  private val activeUop   = Mux(busy, uopReg.uop, 0.U(p(MicroOpWidth).W))
  private val ctrl        = isaImpl.decode(activeUop)

  private val trapRet       = busy && isaImpl.isTrapReturn(activeInstr, activeUop)
  private val syncException = busy && isaImpl.hasSyncException(activeInstr, activeUop)
  private val syncCause     = isaImpl.syncExceptionCause(activeInstr, activeUop)

  private val localTrapUpdate = Wire(new CsrTrapUpdate)

  localTrapUpdate.valid := syncException
  localTrapUpdate.pc    := uopReg.pc
  localTrapUpdate.cause := syncCause

  private val trapUpdate = Wire(new CsrTrapUpdate)

  trapUpdate.valid := localTrapUpdate.valid || io.ctrl.trap_update.valid
  trapUpdate.pc    := Mux(localTrapUpdate.valid, localTrapUpdate.pc, io.ctrl.trap_update.pc)
  trapUpdate.cause := Mux(localTrapUpdate.valid, localTrapUpdate.cause, io.ctrl.trap_update.cause)

  private val csrAddr  = isaImpl.getAddr(activeInstr)
  private val hits     = addrMap.map(_ === csrAddr)
  private val hitAny   = hits.reduce(_ || _)
  private val wrHits   = csrTable.zip(hits).map { case ((reg, _), hit) => hit && reg.writable.B }
  private val wrHitAny = wrHits.reduce(_ || _)
  private val wrAllow  = hitAny && wrHitAny && !ctrl.is_sys
  private val srcData  = Mux(ctrl.is_imm, uopReg.imm, uopReg.rs1_data)

  private val trapUpdates = isaImpl.trapEntryUpdates(regNameMap, trapUpdate.pc, trapUpdate.cause)
  private val retUpdates  = isaImpl.trapReturnUpdates(regNameMap)

  io.ctrl.view := isaImpl.view(regNameMap, extraMap)

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
  private val rdData     = Mux(busy && !ctrl.is_sys, readMasked.reduce(_ | _), 0.U(p(XLen).W))
  private val retTarget  = isaImpl.trapReturnTarget(regNameMap)
  private val resp       = WireDefault(0.U.asTypeOf(new FuResp))

  resp.result       := rdData
  resp.rd           := uopReg.rd
  resp.pc           := uopReg.pc
  resp.instr        := uopReg.instr
  resp.rob_tag      := uopReg.rob_tag
  resp.trap_req     := syncException
  resp.trap_target  := isaImpl.trapTarget(io.ctrl.view)
  resp.trap_ret     := trapRet
  resp.trap_ret_tgt := retTarget

  io.fu.resp.bits := resp
}
