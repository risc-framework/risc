package arch.core.csr

import arch.configs._
import arch.core.exception.ExceptionCsrReq
import arch.core.fupool.{ FuReq, FuResp }
import vutils.graph.{ Node, NodeConfig, NodeSelector }
import chisel3._

class Csr(implicit p: Parameters) extends Node[Parameters]("csr") {
  override protected def cfg: NodeConfig = NodeConfig(
    selector = NodeSelector(
      CsrDims.FILE -> p(ISA).name,
      CsrDims.SYNC -> p(ISA).name,
      CsrDims.IR   -> p(ISA).name
    )
  )

  val fuReq    = inD[FuReq]
  val fuResp   = outD[FuResp]
  val flush    = in[ExceptionCsrReq]
  val ctrlReq  = in[CsrCtrlReq]
  val ctrlResp = out[CsrCtrlResp]

  private val fileImpl = CsrFileFactory.select(cfg)
  private val syncImpl = CsrSyncFactory.select(cfg)
  private val irImpl   = CsrIrFactory.select(cfg)

  private val busy   = RegInit(false.B)
  private val uopReg = RegInit(0.U.asTypeOf(new FuReq))

  private val csrTable = fileImpl.table
  private val csrRegs  = csrTable.map { case (reg, _) =>
    val r = RegInit(reg.initValue.U(p(XLen).W))
    r.suggestName(reg.name)
    r
  }

  private val addrMap = csrTable.map { case (reg, _) => reg.addr.U(fileImpl.addrWidth.W) }
  private val regMap  = csrTable.map(_._1.name).zip(csrRegs).toMap

  private val extraMap = Map(
    "cycle"     -> ctrlReq.in.cycle,
    "instret"   -> ctrlReq.in.instret,
    "timer_irq" -> ctrlReq.in.irq.timer_irq.asUInt,
    "soft_irq"  -> ctrlReq.in.irq.soft_irq.asUInt,
    "ext_irq"   -> ctrlReq.in.irq.ext_irq.asUInt
  )

  private val view = syncImpl.view(regMap, extraMap)
  private val ir   = irImpl.command(regMap, extraMap)

  ctrlResp.out.view := view
  ctrlResp.out.ir   := ir

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
  private val fileCmd     =
    fileImpl.command(activeInstr, activeUop, uopReg.rs1, uopReg.rd, uopReg.rs1_data, uopReg.imm)
  private val syncCmd     = syncImpl.command(activeInstr, activeUop, view)

  private val hits         = addrMap.map(_ === fileCmd.addr)
  private val hitAny       = hits.reduce(_ || _)
  private val readableHits =
    csrTable.zip(hits).map { case ((reg, _), hit) => hit && reg.readable.B }
  private val writableHits =
    csrTable.zip(hits).map { case ((reg, _), hit) => hit && reg.writable.B }
  private val readLegal    = !fileCmd.read || readableHits.reduce(_ || _)
  private val writeLegal   = !fileCmd.write || writableHits.reduce(_ || _)

  private val illegalFileAccess = busy && fileCmd.valid && (!hitAny || !readLegal || !writeLegal)
  private val syncValid         = busy && (syncCmd.valid || illegalFileAccess)
  private val syncKind          = Mux(illegalFileAccess, syncImpl.illegalAccessKind(fileCmd), syncCmd.kind)
  private val syncTarget        = Mux(illegalFileAccess, syncImpl.trapTarget(view), syncCmd.target)

  private val trapUpdates  = syncImpl.trapUpdates(regMap, ctrlReq.in.trap_update)
  private val writeAllowed =
    busy && fileCmd.valid && fileCmd.write && !syncValid && hitAny && writeLegal

  for (((reg, behavior), i) <- csrTable.zipWithIndex) {
    val trapUpdateHit = ctrlReq.in.trap_update.valid && trapUpdates.contains(reg.name).B
    val trapUpdateVal = trapUpdates.getOrElse(reg.name, 0.U(p(XLen).W))
    val next          = WireDefault(csrRegs(i))

    behavior match {
      case AlwaysUpdate(fn)      => next := fn(extraMap)
      case ConditionalUpdate(fn) => next := fn(extraMap)
      case NormalUpdate          => next := csrRegs(i)
    }

    when(trapUpdateHit) {
      next := trapUpdateVal
    }.elsewhen(writeAllowed && hits(i) && reg.writable.B) {
      next := fileImpl.write(csrRegs(i), fileCmd)
    }

    csrRegs(i) := next
  }

  private val readMasked =
    hits.zip(csrRegs).map { case (hit, data) => Mux(hit, data, 0.U(p(XLen).W)) }
  private val rdData     = Mux(
    busy && fileCmd.valid && fileCmd.read && !syncValid,
    readMasked.reduce(_ | _),
    0.U(p(XLen).W)
  )
  private val resp       = WireDefault(0.U.asTypeOf(new FuResp))

  resp.result      := rdData
  resp.rd          := uopReg.rd
  resp.pc          := uopReg.pc
  resp.instr       := uopReg.instr
  resp.rob_tag     := uopReg.rob_tag
  resp.trap_req    := syncValid
  resp.trap_kind   := syncKind
  resp.trap_target := syncTarget

  fuResp.out.bits := resp
}
