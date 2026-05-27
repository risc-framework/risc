package arch.core.fu.builtin

import arch.core.fu._
import arch.core.imm.ImmUtilsFactory
import arch.core.pma.PmaChecker
import arch.core.uop.MicroOp
import arch.core.lsu.{ StoreWriteBundle, StoreUtilsFactory }
import arch.configs._
import chisel3._
import chisel3.util.{ Valid, is, switch }

object StoreFUState extends ChiselEnum {
  val IDLE, WRITE_SB, DONE = Value
}

class StoreFU(implicit p: Parameters) extends FunctionalUnit {
  override def desiredName: String = s"${p(ISA).name}_store_fu"

  override def fuType: FunctionalUnitType =
    FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST

  val sbWrite = IO(Valid(new StoreWriteBundle))
  val busy    = IO(Output(Bool()))

  private val utils    = StoreUtilsFactory.getOrThrow(p(ISA).name)
  private val immUtils = ImmUtilsFactory.getOrThrow(p(ISA).name)

  private val state  = RegInit(StoreFUState.IDLE)
  private val uopReg = Reg(new MicroOp)

  private val ctrl = utils.decodeStore(uopReg.uop)
  private val imm  = immUtils.genImm(uopReg.instr, uopReg.imm_type)
  private val addr = uopReg.rs1_data + imm

  private val alignedAddr = utils.alignedAddr(addr)
  private val storeData   = utils.alignedStoreData(ctrl, addr, uopReg.rs2_data)
  private val storeMask   = utils.shiftedStoreMask(ctrl, addr)

  private val (_, _, _, pmaCacheable) = PmaChecker(addr)

  busy := state =/= StoreFUState.IDLE

  io.req.ready :=
    !io.flush &&
      (state === StoreFUState.IDLE ||
        (state === StoreFUState.DONE && io.resp.ready))

  private val acceptFire = io.req.fire && !io.flush

  sbWrite.valid          := state === StoreFUState.WRITE_SB
  sbWrite.bits.sq_idx    := uopReg.sq_idx
  sbWrite.bits.rob_tag   := uopReg.rob_tag
  sbWrite.bits.addr      := alignedAddr
  sbWrite.bits.data      := storeData
  sbWrite.bits.mask      := storeMask
  sbWrite.bits.cacheable := pmaCacheable

  io.resp.valid := state === StoreFUState.DONE && !io.flush
  io.resp.bits  := defaultResp(uopReg, 0.U(p(XLen).W))

  when(io.flush) {
    state := StoreFUState.IDLE
  }.otherwise {
    switch(state) {
      is(StoreFUState.IDLE) {}

      is(StoreFUState.WRITE_SB) {
        state := StoreFUState.DONE
      }

      is(StoreFUState.DONE) {
        when(io.resp.fire) {
          state := StoreFUState.IDLE
        }
      }
    }

    when(acceptFire) {
      uopReg := io.req.bits
      state  := StoreFUState.WRITE_SB
    }
  }
}

object StoreFUBuilder extends RegisteredFunctionalUnitBuilder {
  override lazy val utils: FunctionalUnitBuilder = new FunctionalUnitBuilder {
    override def name: String                                  = "st"
    override def fuType: FunctionalUnitType                    = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST
    override def build(implicit p: Parameters): FunctionalUnit = new StoreFU
  }
}
