package arch.core.lsu

import arch.core.ooo._
import arch.core.imm._
import arch.core.pma._
import arch.configs._
import vcache._
import chisel3._
import chisel3.util.{ switch, is }

object LoadFUState extends ChiselEnum {
  val IDLE, FWD_REQ, FWD_RESP, MEM_REQ, WAIT_MEM, DONE, FLUSH_DRAIN = Value
}

class LoadCtrl(implicit p: Parameters) extends Bundle {
  val is_byte     = Bool()
  val is_half     = Bool()
  val is_word     = Bool()
  val is_dword    = Bool()
  val is_unsigned = Bool()
  val strb        = UInt(p(BytesPerWord).W)
}

class LoadFU(implicit p: Parameters) extends FunctionalUnit {
  override def desiredName: String = s"${p(ISA).name}_load_fu"

  val mem           = IO(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams)))
  val mmio          = IO(new CachePortIO(UInt(p(XLen).W), p(L1DCacheParams)))
  val sbFwd         = IO(Flipped(new StoreForwardPort))
  val sbOldestValid = IO(Input(Bool()))
  val sbOldestSeq   = IO(Input(UInt(64.W)))
  val busy          = IO(Output(Bool()))

  val utils    = LoadUtilsFactory.getOrThrow(p(ISA).name)
  val immUtils = ImmUtilsFactory.getOrThrow(p(ISA).name)

  val state           = RegInit(LoadFUState.IDLE)
  val uopReg          = Reg(new MicroOp)
  val ctrlReg         = RegInit(0.U.asTypeOf(new LoadCtrl))
  val addrReg         = RegInit(0.U(p(XLen).W))
  val alignedAddrReg  = RegInit(0.U(p(XLen).W))
  val loadMaskReg     = RegInit(0.U(p(BytesPerWord).W))
  val pmaCacheableReg = RegInit(false.B)
  val resultReg       = RegInit(0.U(p(XLen).W))
  val fwdDataReg      = RegInit(0.U(p(XLen).W))
  val fwdMaskReg      = RegInit(0.U(p(BytesPerWord).W))
  val reqOutstanding  = RegInit(false.B)
  val reqWasCache     = RegInit(false.B)

  val acceptCtrl                    = utils.decodeLoad(io.req.bits.uop)
  val acceptImm                     = immUtils.genImm(io.req.bits.instr, io.req.bits.imm_type)
  val acceptAddr                    = io.req.bits.rs1_data + acceptImm
  val acceptAlignedAddr             = utils.alignedAddr(acceptAddr)
  val acceptLoadMask                = utils.shiftedLoadMask(acceptCtrl, acceptAddr)
  val (_, _, _, acceptPmaCacheable) = PmaChecker(acceptAddr)
  val acceptHasOlderStore           = sbOldestValid && sbOldestSeq < io.req.bits.sq_seq

  val fwdResp           = sbFwd.resp.bits
  val fwdRespFire       = sbFwd.resp.fire
  val mmioOrderBlock    = !pmaCacheableReg && fwdResp.hasOlder
  val shouldBlock       = fwdResp.block || mmioOrderBlock
  val fullForward       = pmaCacheableReg && fwdResp.fwdFull
  val partialForward    = pmaCacheableReg && fwdResp.fwdValid && !fwdResp.fwdFull
  val fwdCompleteNow    = state === LoadFUState.FWD_RESP && sbFwd.resp.valid && !shouldBlock && fullForward && !io.flush
  val canSendMemFromFwd = state === LoadFUState.FWD_RESP && sbFwd.resp.valid && !shouldBlock && !fullForward && !io.flush

  mem.resp.ready  := (state === LoadFUState.WAIT_MEM || state === LoadFUState.FLUSH_DRAIN) && reqWasCache
  mmio.resp.ready := (state === LoadFUState.WAIT_MEM || state === LoadFUState.FLUSH_DRAIN) && !reqWasCache

  val memReqFire       = mem.req.fire || mmio.req.fire
  val memRespFire      = mem.resp.fire || mmio.resp.fire
  val memRespData      = Mux(reqWasCache, mem.resp.bits.data, mmio.resp.bits.data)
  val expandedFwdMask  = utils.expandByteMask(fwdMaskReg)
  val mergedBusData    = (memRespData & ~expandedFwdMask) | (fwdDataReg & expandedFwdMask)
  val fwdResult        = utils.loadResult(ctrlReg, addrReg, fwdResp.fwdData)
  val memResult        = utils.loadResult(ctrlReg, addrReg, mergedBusData)
  val memCompleteNow   = state === LoadFUState.WAIT_MEM && memRespFire && !io.flush
  val doneCompleteNow  = state === LoadFUState.DONE && !io.flush
  val currentRespValid = fwdCompleteNow || memCompleteNow || doneCompleteNow
  val currentRespFire  = currentRespValid && io.resp.ready

  busy         := state =/= LoadFUState.IDLE
  io.req.ready := !io.flush && (state === LoadFUState.IDLE || currentRespFire)

  val acceptFire        = io.req.fire && !io.flush
  val fwdReqFromAccept  = acceptFire && acceptHasOlderStore
  val fwdReqFromRetry   = state === LoadFUState.FWD_REQ && !io.flush
  val fwdReqUsingAccept = fwdReqFromAccept

  sbFwd.req.valid       := fwdReqFromAccept || fwdReqFromRetry
  sbFwd.req.bits.valid  := true.B
  sbFwd.req.bits.sq_seq := Mux(fwdReqUsingAccept, io.req.bits.sq_seq, uopReg.sq_seq)
  sbFwd.req.bits.addr   := Mux(fwdReqUsingAccept, acceptAlignedAddr, alignedAddrReg)
  sbFwd.req.bits.mask   := Mux(fwdReqUsingAccept, acceptLoadMask, loadMaskReg)
  sbFwd.resp.ready      := state === LoadFUState.FWD_RESP && !io.flush

  val memReqFromAccept = acceptFire && !acceptHasOlderStore
  val memReqFromRetry  = state === LoadFUState.MEM_REQ && !io.flush
  val memReqFromFwd    = canSendMemFromFwd
  val memReqActive     = memReqFromAccept || memReqFromRetry || memReqFromFwd

  val memReqCacheable = Mux(memReqFromAccept, acceptPmaCacheable, pmaCacheableReg)
  val memReqAddr      = Mux(memReqFromAccept, acceptAlignedAddr, alignedAddrReg)
  val memReqMask      = Mux(memReqFromAccept, acceptLoadMask, loadMaskReg)

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

  io.resp.valid        := currentRespValid
  io.resp.bits.result  := Mux(fwdCompleteNow, fwdResult, Mux(memCompleteNow, memResult, resultReg))
  io.resp.bits.rd      := uopReg.rd
  io.resp.bits.pc      := uopReg.pc
  io.resp.bits.instr   := uopReg.instr
  io.resp.bits.rob_tag := uopReg.rob_tag

  when(memReqFire || memRespFire) {
    reqOutstanding := (reqOutstanding && !memRespFire) || memReqFire
  }

  when(memReqFire) {
    reqWasCache := memReqCacheable
  }

  val willHaveOutstanding = (reqOutstanding && !memRespFire) || memReqFire

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
