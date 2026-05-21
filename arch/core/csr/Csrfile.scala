package arch.core.csr

import arch.core.regfile.Register
import arch.configs._
import chisel3._

class CsrCtrl(val opWidth: Int) extends Bundle {
  val is_sys = Bool()
  val is_imm = Bool()
  val op     = UInt(opWidth.W)
}

class CsrFile(implicit p: Parameters) extends Module {
  override def desiredName: String = s"${p(ISA).name}_csrfile"

  val utils = CsrUtilsFactory.getOrThrow(p(ISA).name)

  val en    = IO(Input(Bool()))
  val uop   = IO(Input(UInt(p(MicroOpWidth).W)))
  val instr = IO(Input(UInt(p(ILen).W)))
  val addr  = IO(Input(UInt(utils.addrWidth.W)))
  val src   = IO(Input(UInt(p(XLen).W)))
  val pc    = IO(Input(UInt(p(XLen).W)))
  val rd    = IO(Output(UInt(p(XLen).W)))

  // Trap Entry
  val trap_request = IO(Output(Bool()))
  val trap_target  = IO(Output(UInt(p(XLen).W)))

  // Trap Return
  val trap_ret_target = IO(Output(UInt(p(XLen).W)))

  val extraInputIO: Map[String, UInt] = utils.extraInputs.map { case (name, width) =>
    val port = IO(Input(UInt(width.W)))
    port.suggestName(s"extra_$name")
    name -> port
  }.toMap

  val csrTable: Seq[Register] = utils.table.map(_._1)
  val addrMap: Seq[UInt]      = csrTable.map(_.addr.U(utils.addrWidth.W))

  val csrRegs: Seq[UInt] = csrTable.zipWithIndex.map { case (reg, _) =>
    val r = RegInit(reg.initValue.U(p(XLen).W))
    r.suggestName(reg.name)
    r
  }

  val regNameMap: Map[String, UInt] = csrTable.map(_.name).zip(csrRegs).toMap

  val (do_trap, target, cause) = utils.checkInterrupts(regNameMap, extraInputIO)
  trap_request := do_trap
  trap_target  := target

  val trapUpdates = utils.getTrapUpdates(regNameMap, pc, cause)

  trap_ret_target := utils.getTrapReturnTarget(regNameMap)
  val retUpdates = utils.getTrapReturnUpdates(regNameMap)

  val ctrl     = utils.decode(uop)
  val trap_ret = en && ctrl.is_sys

  val hits: Seq[Bool]          = addrMap.map(_ === addr)
  val addrMatch: Bool          = hits.reduce(_ || _)
  val writableHits: Seq[Bool]  = csrTable.zip(hits).map { case (reg, h) => h && reg.writable.B }
  val writableAddrMatch: Bool  = writableHits.reduce(_ || _)
  val writeAccessAllowed: Bool = addrMatch && writableAddrMatch && !ctrl.is_sys

  val srcData: UInt = Mux(ctrl.is_imm, utils.genImm(instr), src)

  utils.table.zipWithIndex.foreach { case ((reg, behavior), i) =>
    val isTrapUpdatingThisReg = do_trap && trapUpdates.contains(reg.name).B
    val trapUpdateValue       = trapUpdates.getOrElse(reg.name, 0.U)

    val isRetUpdatingThisReg = trap_ret && retUpdates.contains(reg.name).B
    val retUpdateValue       = retUpdates.getOrElse(reg.name, 0.U)

    behavior match {
      case AlwaysUpdate(fn) =>
        csrRegs(i) := fn(extraInputIO)

      case ConditionalUpdate(fn) =>
        csrRegs(i) := fn(extraInputIO)
        when(isTrapUpdatingThisReg) {
          csrRegs(i) := trapUpdateValue
        }.elsewhen(isRetUpdatingThisReg) {
          csrRegs(i) := retUpdateValue
        }.elsewhen(en && writeAccessAllowed && hits(i) && reg.writable.B) {
          csrRegs(i) := utils.fn(ctrl.op, csrRegs(i), srcData)
        }

      case NormalUpdate =>
        when(isTrapUpdatingThisReg) {
          csrRegs(i) := trapUpdateValue
        }.elsewhen(isRetUpdatingThisReg) {
          csrRegs(i) := retUpdateValue
        }.elsewhen(en && writeAccessAllowed && hits(i) && reg.writable.B) {
          csrRegs(i) := utils.fn(ctrl.op, csrRegs(i), srcData)
        }
    }
  }

  val readCases: Seq[(Bool, UInt)] = hits.zip(csrRegs)

  val read_w      = readCases.head._2.getWidth
  val read_masked = readCases.map { case (cond, v) => Mux(cond, v, 0.U(read_w.W)) }
  rd := Mux(en && !ctrl.is_sys, read_masked.reduce(_ | _), 0.U(p(XLen).W))
}
