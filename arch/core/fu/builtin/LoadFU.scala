package arch.core.fu.builtin

import arch.core.fu._
import arch.core.imm.ImmUtilsFactory
import arch.core.pma.PmaChecker
import arch.core.uop.MicroOp
import arch.core.lsu.{ LoadUtilsFactory, StoreForwardPort, LoadCtrl }
import arch.configs._
import vcache.{ CachePortIO, CacheCommand }
import chisel3._
import chisel3.util.{ is, switch }

object LoadFUState extends ChiselEnum {
  val IDLE, FWD_REQ, FWD_RESP, MEM_REQ, WAIT_MEM, DONE, FLUSH_DRAIN = Value
}

class LoadFU(implicit p: Parameters) extends FunctionalUnit {
  override def desiredName: String = s"${p(ISA).name}_load_fu"

  override def fuType: FunctionalUnitType =
    FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD

  val mem           = IO(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams)))
  val mmio          = IO(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams)))
  val sbFwd         = IO(Flipped(new StoreForwardPort))
  val sbOldestValid = IO(Input(Bool()))
  val sbOldestSeq   = IO(Input(UInt(64.W)))
  val busy          = IO(Output(Bool()))

  private val utils    = LoadUtilsFactory.getOrThrow(p(ISA).name)
  private val immUtils = ImmUtilsFactory.getOrThrow(p(ISA).name)

  private val state           = RegInit(LoadFUState.IDLE)
  private val uopReg          = Reg(new MicroOp)
  private val ctrlReg         = RegInit(0.U.asTypeOf(new LoadCtrl))
  private val addrReg         = RegInit(0.U(p(XLen).W))
  private val alignedAddrReg  = RegInit(0.U(p(XLen).W))
  private val loadMaskReg     = RegInit(0.U(p(BytesPerWord).W))
  private val pmaCacheableReg = RegInit(false.B)
  private val resultReg       = RegInit(0.U(p(XLen).W))
  private val fwdDataReg      = RegInit(0.U(p(XLen).W))
  private val fwdMaskReg      = RegInit(0.U(p(BytesPerWord).W))
  private val reqOutstanding  = RegInit(false.B)
  private val reqWasCache     = RegInit(false.B)

  private val acceptCtrl                    = utils.decodeLoad(io.req.bits.uop)
  private val acceptImm                     = immUtils.genImm(io.req.bits.instr, io.req.bits.imm_type)
  private val acceptAddr                    = io.req.bits.rs1_data + acceptImm
  private val acceptAlignedAddr             = utils.alignedAddr(acceptAddr)
  private val acceptLoadMask                = utils.shiftedLoadMask(acceptCtrl, acceptAddr)
  private val (_, _, _, acceptPmaCacheable) = PmaChecker(acceptAddr)
  private val acceptHasOlderStore           = sbOldestValid && sbOldestSeq < io.req.bits.sq_seq

  private val fwdResp           = sbFwd.resp.bits
  private val fwdRespFire       = sbFwd.resp.fire
  private val mmioOrderBlock    = !pmaCacheableReg && fwdResp.hasOlder
  private val shouldBlock       = fwdResp.block || mmioOrderBlock
  private val fullForward       = pmaCacheableReg && fwdResp.fwdFull
  private val partialForward    = pmaCacheableReg && fwdResp.fwdValid && !fwdResp.fwdFull
  private val fwdCompleteNow    = state === LoadFUState.FWD_RESP && sbFwd.resp.valid && !shouldBlock && fullForward && !io.flush
  private val canSendMemFromFwd = state === LoadFUState.FWD_RESP && sbFwd.resp.valid && !shouldBlock && !fullForward && !io.flush

  mem.resp.ready  := (state === LoadFUState.WAIT_MEM || state === LoadFUState.FLUSH_DRAIN) && reqWasCache
  mmio.resp.ready := (state === LoadFUState.WAIT_MEM || state === LoadFUState.FLUSH_DRAIN) && !reqWasCache

  private val memReqFire       = mem.req.fire || mmio.req.fire
  private val memRespFire      = mem.resp.fire || mmio.resp.fire
  private val memRespData      = Mux(reqWasCache, mem.resp.bits.data, mmio.resp.bits.data)
  private val expandedFwdMask  = utils.expandByteMask(fwdMaskReg)
  private val mergedBusData    = (memRespData & ~expandedFwdMask) | (fwdDataReg & expandedFwdMask)
  private val fwdResult        = utils.loadResult(ctrlReg, addrReg, fwdResp.fwdData)
  private val memResult        = utils.loadResult(ctrlReg, addrReg, mergedBusData)
  private val memCompleteNow   = state === LoadFUState.WAIT_MEM && memRespFire && !io.flush
  private val doneCompleteNow  = state === LoadFUState.DONE && !io.flush
  private val currentRespValid = fwdCompleteNow || memCompleteNow || doneCompleteNow
  private val currentRespFire  = currentRespValid && io.resp.ready

  busy := state =/= LoadFUState.IDLE

  io.req.ready :=
    !io.flush &&
      (state === LoadFUState.IDLE || currentRespFire)

  private val acceptFire        = io.req.fire && !io.flush
  private val fwdReqFromAccept  = acceptFire && acceptHasOlderStore
  private val fwdReqFromRetry   = state === LoadFUState.FWD_REQ && !io.flush
  private val fwdReqUsingAccept = fwdReqFromAccept

  sbFwd.req.valid       := fwdReqFromAccept || fwdReqFromRetry
  sbFwd.req.bits.valid  := true.B
  sbFwd.req.bits.sq_seq := Mux(fwdReqUsingAccept, io.req.bits.sq_seq, uopReg.sq_seq)
  sbFwd.req.bits.addr   := Mux(fwdReqUsingAccept, acceptAlignedAddr, alignedAddrReg)
  sbFwd.req.bits.mask   := Mux(fwdReqUsingAccept, acceptLoadMask, loadMaskReg)
  sbFwd.resp.ready      := state === LoadFUState.FWD_RESP && !io.flush

  private val memReqFromAccept = acceptFire && !acceptHasOlderStore
  private val memReqFromRetry  = state === LoadFUState.MEM_REQ && !io.flush
  private val memReqFromFwd    = canSendMemFromFwd
  private val memReqActive     = memReqFromAccept || memReqFromRetry || memReqFromFwd

  private val memReqCacheable = Mux(memReqFromAccept, acceptPmaCacheable, pmaCacheableReg)
  private val memReqAddr      = Mux(memReqFromAccept, acceptAlignedAddr, alignedAddrReg)
  private val memReqMask      = Mux(memReqFromAccept, acceptLoadMask, loadMaskReg)

  mem.req.valid       := memReqActive && memReqCacheable && !io.flush
  mem.req.bits.cmd    := CacheCommand.Read
  mem.req.bits.addr   := memReqAddr
  mem.req.bits.data   := 0.U
  mem.req.bits.strb   := memReqMask
  mem.req.bits.source := 0.U

  mmio.req.valid       := memReqActive && !memReqCacheable && !io.flush
  mmio.req.bits.cmd    := CacheCommand.Read
  mmio.req.bits.addr   := memReqAddr
  mmio.req.bits.data   := 0.U
  mmio.req.bits.strb   := memReqMask
  mmio.req.bits.source := 0.U

  private val respResult =
    Mux(fwdCompleteNow, fwdResult, Mux(memCompleteNow, memResult, resultReg))

  io.resp.valid := currentRespValid
  io.resp.bits  := defaultResp(uopReg, respResult)

  when(memReqFire || memRespFire) {
    reqOutstanding := (reqOutstanding && !memRespFire) || memReqFire
  }

  when(memReqFire) {
    reqWasCache := memReqCacheable
  }

  private val willHaveOutstanding = (reqOutstanding && !memRespFire) || memReqFire

  when(io.flush) {
    when(willHaveOutstanding) {
      state := LoadFUState.FLUSH_DRAIN
    }.otherwise {
      state := LoadFUState.IDLE
    }
  }.otherwise {
    switch(state) {
      is(LoadFUState.IDLE) {}

      is(LoadFUState.FWD_REQ) {
        when(sbFwd.req.fire) {
          state := LoadFUState.FWD_RESP
        }
      }

      is(LoadFUState.FWD_RESP) {
        when(fwdRespFire) {
          when(shouldBlock) {
            state := LoadFUState.FWD_REQ
          }.elsewhen(fullForward) {
            when(io.resp.ready) {
              state := LoadFUState.IDLE
            }.otherwise {
              resultReg := fwdResult
              state     := LoadFUState.DONE
            }
          }.otherwise {
            fwdDataReg := Mux(partialForward, fwdResp.fwdData, 0.U)
            fwdMaskReg := Mux(partialForward, fwdResp.fwdMask, 0.U)

            when(memReqFire) {
              state := LoadFUState.WAIT_MEM
            }.otherwise {
              state := LoadFUState.MEM_REQ
            }
          }
        }
      }

      is(LoadFUState.MEM_REQ) {
        when(memReqFire) {
          state := LoadFUState.WAIT_MEM
        }
      }

      is(LoadFUState.WAIT_MEM) {
        when(memRespFire) {
          when(io.resp.ready) {
            state := LoadFUState.IDLE
          }.otherwise {
            resultReg := memResult
            state     := LoadFUState.DONE
          }
        }
      }

      is(LoadFUState.DONE) {
        when(io.resp.fire) {
          state := LoadFUState.IDLE
        }
      }

      is(LoadFUState.FLUSH_DRAIN) {
        when(memRespFire) {
          state := LoadFUState.IDLE
        }
      }
    }

    when(acceptFire) {
      uopReg          := io.req.bits
      ctrlReg         := acceptCtrl
      addrReg         := acceptAddr
      alignedAddrReg  := acceptAlignedAddr
      loadMaskReg     := acceptLoadMask
      pmaCacheableReg := acceptPmaCacheable
      resultReg       := 0.U
      fwdDataReg      := 0.U
      fwdMaskReg      := 0.U

      when(acceptHasOlderStore) {
        state := Mux(sbFwd.req.fire, LoadFUState.FWD_RESP, LoadFUState.FWD_REQ)
      }.otherwise {
        state := Mux(memReqFire, LoadFUState.WAIT_MEM, LoadFUState.MEM_REQ)
      }
    }
  }
}

object LoadFUBuilder extends RegisteredFUBuilder {
  override lazy val utils: FUBuilder = new FUBuilder {
    override def name: String                                  = "ld"
    override def fuType: FunctionalUnitType                    = FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD
    override def build(implicit p: Parameters): FunctionalUnit = new LoadFU
  }
}
