package arch.core.ld

import arch.core.pma.PmaModeFactory
import arch.core.fupool.{ FuFlushReq, FuResp }
import arch.core.fupool.FuReq
import arch.core.memarb.{ MemoryArbiterCacheReq, MemoryArbiterCacheResp }
import arch.core.sb.{ StoreBufferStatus, StoreForwardReq, StoreForwardResp }
import arch.configs._
import vcache.CacheCommand
import vutils.graph.{ Node, NodeConfig, NodeSelector }
import chisel3._
import chisel3.util.{ is, switch }

object LdState extends ChiselEnum {
  val IDLE, FWD_REQ, FWD_RESP, MEM_REQ, WAIT_MEM, DONE, FLUSH_DRAIN = Value
}

class Ld(implicit p: Parameters) extends Node[Parameters]("ld") {
  override protected def cfg: NodeConfig = NodeConfig(
    selector = NodeSelector(
      LdDims.ISA -> p(ISA).name
    )
  )

  val fuReq  = inD[FuReq]
  val fuResp = outD[FuResp]
  val flush  = in[FuFlushReq]

  val memReq   = outD[MemoryArbiterCacheReq]
  val memResp  = inD[MemoryArbiterCacheResp]
  val mmioReq  = outD[MemoryArbiterCacheReq]
  val mmioResp = inD[MemoryArbiterCacheResp]

  val fwdReq   = outD[StoreForwardReq]
  val fwdResp  = inD[StoreForwardResp]
  val sbStatus = in[StoreBufferStatus]

  private val isaImpl = LdIsaFactory.select(cfg)
  private val pma     = PmaModeFactory.getOrThrow("default")

  private val state           = RegInit(LdState.IDLE)
  private val uopReg          = Reg(new FuReq)
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

  private val acceptCtrl          = isaImpl.decodeLoad(fuReq.in.bits.uop)
  private val acceptImm           = fuReq.in.bits.imm
  private val acceptAddr          = fuReq.in.bits.rs1_data + acceptImm
  private val acceptAlignedAddr   = isaImpl.alignedAddr(acceptAddr)
  private val acceptLoadMask      = isaImpl.shiftedLoadMask(acceptCtrl, acceptAddr)
  private val acceptPmaResult     = pma.check(acceptAddr)
  private val acceptHasOlderStore =
    sbStatus.in.oldest_valid && sbStatus.in.oldest_seq < fuReq.in.bits.sq_seq

  private val fwdRespBits    = fwdResp.in.bits
  private val fwdRespFire    = fwdResp.in.fire
  private val mmioOrderBlock = !pmaCacheableReg && fwdRespBits.has_older
  private val shouldBlock    = fwdRespBits.block || mmioOrderBlock
  private val fullForward    = pmaCacheableReg && fwdRespBits.full
  private val partialForward = pmaCacheableReg && fwdRespBits.valid && !fwdRespBits.full

  private val fwdCompleteNow =
    state === LdState.FWD_RESP && fwdResp.in.valid && !shouldBlock && fullForward && !flush.in.flush

  private val canSendMemFromFwd =
    state === LdState.FWD_RESP && fwdResp.in.valid && !shouldBlock && !fullForward && !flush.in.flush

  memResp.in.ready  := (state === LdState.WAIT_MEM || state === LdState.FLUSH_DRAIN) && reqWasCache
  mmioResp.in.ready := (state === LdState.WAIT_MEM || state === LdState.FLUSH_DRAIN) && !reqWasCache

  private val memReqFire  = memReq.out.fire || mmioReq.out.fire
  private val memRespFire = memResp.in.fire || mmioResp.in.fire
  private val memRespData = Mux(reqWasCache, memResp.in.bits.data, mmioResp.in.bits.data)

  private val expandedFwdMask = isaImpl.expandByteMask(fwdMaskReg)
  private val mergedBusData   = (memRespData & ~expandedFwdMask) | (fwdDataReg & expandedFwdMask)
  private val fwdResult       = isaImpl.loadResult(ctrlReg, addrReg, fwdRespBits.data)
  private val memResult       = isaImpl.loadResult(ctrlReg, addrReg, mergedBusData)

  private val memCompleteNow   = state === LdState.WAIT_MEM && memRespFire && !flush.in.flush
  private val doneCompleteNow  = state === LdState.DONE && !flush.in.flush
  private val currentRespValid = fwdCompleteNow || memCompleteNow || doneCompleteNow
  private val currentRespFire  = currentRespValid && fuResp.out.ready

  fuReq.in.ready := !flush.in.flush && (state === LdState.IDLE || currentRespFire)

  private val acceptFire = fuReq.in.fire && !flush.in.flush

  private val fwdReqFromAccept  = acceptFire && acceptHasOlderStore
  private val fwdReqFromRetry   = state === LdState.FWD_REQ && !flush.in.flush
  private val fwdReqUsingAccept = fwdReqFromAccept

  fwdReq.out.valid       := fwdReqFromAccept || fwdReqFromRetry
  fwdReq.out.bits.valid  := true.B
  fwdReq.out.bits.sq_seq := Mux(fwdReqUsingAccept, fuReq.in.bits.sq_seq, uopReg.sq_seq)
  fwdReq.out.bits.addr   := Mux(fwdReqUsingAccept, acceptAlignedAddr, alignedAddrReg)
  fwdReq.out.bits.mask   := Mux(fwdReqUsingAccept, acceptLoadMask, loadMaskReg)
  fwdResp.in.ready       := state === LdState.FWD_RESP && !flush.in.flush

  private val memReqFromAccept = acceptFire && !acceptHasOlderStore
  private val memReqFromRetry  = state === LdState.MEM_REQ && !flush.in.flush
  private val memReqFromFwd    = canSendMemFromFwd
  private val memReqActive     = memReqFromAccept || memReqFromRetry || memReqFromFwd

  private val memReqCacheable = Mux(memReqFromAccept, acceptPmaResult.cacheable, pmaCacheableReg)
  private val memReqAddr      = Mux(memReqFromAccept, acceptAlignedAddr, alignedAddrReg)
  private val memReqMask      = Mux(memReqFromAccept, acceptLoadMask, loadMaskReg)

  memReq.out.valid       := memReqActive && memReqCacheable && !flush.in.flush
  memReq.out.bits.cmd    := CacheCommand.Read
  memReq.out.bits.addr   := memReqAddr
  memReq.out.bits.data   := 0.U
  memReq.out.bits.strb   := memReqMask
  memReq.out.bits.source := 0.U

  mmioReq.out.valid       := memReqActive && !memReqCacheable && !flush.in.flush
  mmioReq.out.bits.cmd    := CacheCommand.Read
  mmioReq.out.bits.addr   := memReqAddr
  mmioReq.out.bits.data   := 0.U
  mmioReq.out.bits.strb   := memReqMask
  mmioReq.out.bits.source := 0.U

  private val respResult = Mux(fwdCompleteNow, fwdResult, Mux(memCompleteNow, memResult, resultReg))
  private val resp       = WireDefault(0.U.asTypeOf(new FuResp))

  resp.result  := respResult
  resp.rd      := uopReg.rd
  resp.pc      := uopReg.pc
  resp.instr   := uopReg.instr
  resp.rob_tag := uopReg.rob_tag

  fuResp.out.valid := currentRespValid
  fuResp.out.bits  := resp

  when(memReqFire || memRespFire) {
    reqOutstanding := (reqOutstanding && !memRespFire) || memReqFire
  }

  when(memReqFire) {
    reqWasCache := memReqCacheable
  }

  private val willHaveOutstanding = (reqOutstanding && !memRespFire) || memReqFire

  when(flush.in.flush) {
    when(willHaveOutstanding) {
      state := LdState.FLUSH_DRAIN
    }.otherwise {
      state := LdState.IDLE
    }
  }.otherwise {
    switch(state) {
      is(LdState.IDLE) {}

      is(LdState.FWD_REQ) {
        when(fwdReq.out.fire) {
          state := LdState.FWD_RESP
        }
      }

      is(LdState.FWD_RESP) {
        when(fwdRespFire) {
          when(shouldBlock) {
            state := LdState.FWD_REQ
          }.elsewhen(fullForward) {
            when(fuResp.out.ready) {
              state := LdState.IDLE
            }.otherwise {
              resultReg := fwdResult
              state     := LdState.DONE
            }
          }.otherwise {
            fwdDataReg := Mux(partialForward, fwdRespBits.data, 0.U)
            fwdMaskReg := Mux(partialForward, fwdRespBits.mask, 0.U)

            when(memReqFire) {
              state := LdState.WAIT_MEM
            }.otherwise {
              state := LdState.MEM_REQ
            }
          }
        }
      }

      is(LdState.MEM_REQ) {
        when(memReqFire) {
          state := LdState.WAIT_MEM
        }
      }

      is(LdState.WAIT_MEM) {
        when(memRespFire) {
          when(fuResp.out.ready) {
            state := LdState.IDLE
          }.otherwise {
            resultReg := memResult
            state     := LdState.DONE
          }
        }
      }

      is(LdState.DONE) {
        when(fuResp.out.fire) {
          state := LdState.IDLE
        }
      }

      is(LdState.FLUSH_DRAIN) {
        when(memRespFire) {
          state := LdState.IDLE
        }
      }
    }

    when(acceptFire) {
      uopReg          := fuReq.in.bits
      ctrlReg         := acceptCtrl
      addrReg         := acceptAddr
      alignedAddrReg  := acceptAlignedAddr
      loadMaskReg     := acceptLoadMask
      pmaCacheableReg := acceptPmaResult.cacheable
      resultReg       := 0.U
      fwdDataReg      := 0.U
      fwdMaskReg      := 0.U

      when(acceptHasOlderStore) {
        state := Mux(fwdReq.out.fire, LdState.FWD_RESP, LdState.FWD_REQ)
      }.otherwise {
        state := Mux(memReqFire, LdState.WAIT_MEM, LdState.MEM_REQ)
      }
    }
  }
}
