package arch.node.ld

import arch.core.pma.PmaChecker
import arch.node.fupool.FuResp
import arch.node.imm.ImmIsaFactory
import arch.node.uop.MicroOp
import arch.configs._
import vcache.CacheCommand
import vutils.graph.{ Node, NodeConfig, NodeSelector, NodeType }
import chisel3._
import chisel3.util.{ Fill, is, switch }

object LdState extends ChiselEnum {
  val IDLE, FWD_REQ, FWD_RESP, MEM_REQ, WAIT_MEM, DONE, FLUSH_DRAIN = Value
}

class Ld(implicit p: Parameters) extends Node(new LdIO) {
  private val cfg = NodeConfig(
    selector = NodeSelector(
      LdDims.ISA -> p(ISA).name
    )
  )

  override def nodeType: NodeType  = LdMeta.Type
  override def desiredName: String = s"ld_${cfg.selector.canonicalName}"

  private val isaImpl         = LdIsaFactory.select(cfg)
  private val imm             = ImmIsaFactory.select(p(ISA).name)
  private val state           = RegInit(LdState.IDLE)
  private val uopReg          = Reg(new MicroOp)
  private val ctrlReg         = RegInit(0.U.asTypeOf(new LoadCtrl))
  private val addrReg         = RegInit(0.U(p(XLen).W))
  private val alignedAddrReg  = RegInit(0.U(p(XLen).W))
  private val loadMaskReg     = RegInit(0.U(p(BytesPerWord).W))
  private val pmaCacheableReg = RegInit(false.B)
  private val resultReg       = RegInit(0.U(p(XLen).W))
  private val fwdDataReg      = RegInit(0.U(p(XLen).W))
  private val fwdMaskReg      = RegInit(0.U(p(BytesPerWord).W))
  private val sqSeqReg        = RegInit(0.U(64.W))
  private val reqOutstanding  = RegInit(false.B)
  private val reqWasCache     = RegInit(false.B)

  private val acceptCtrl                    = isaImpl.decodeLoad(io.fu.req.bits.uop)
  private val acceptImm                     = imm.gen(io.fu.req.bits.instr, io.fu.req.bits.imm_type)
  private val acceptAddr                    = io.fu.req.bits.rs1_data + acceptImm
  private val acceptAlignedAddr             = isaImpl.alignedAddr(acceptAddr)
  private val acceptRawLoadMask             = isaImpl.shiftedLoadMask(acceptCtrl, acceptAddr)
  private val acceptLoadMask                =
    Mux(acceptRawLoadMask.orR, acceptRawLoadMask, Fill(p(BytesPerWord), 1.U(1.W)).asUInt)
  private val (_, _, _, acceptPmaCacheable) = PmaChecker(acceptAddr)

  private val fwdResp        = io.sb.sb_fwd.resp.bits
  private val fwdRespFire    = io.sb.sb_fwd.resp.fire
  private val mmioOrderBlock = !pmaCacheableReg && fwdResp.has_older
  private val shouldBlock    = fwdResp.block || mmioOrderBlock
  private val fullForward    = pmaCacheableReg && fwdResp.full
  private val partialForward = pmaCacheableReg && fwdResp.valid && !fwdResp.full

  private val fwdCompleteNow    =
    state === LdState.FWD_RESP && io.sb.sb_fwd.resp.valid && !shouldBlock && fullForward && !io.fu.flush
  private val canSendMemFromFwd =
    state === LdState.FWD_RESP && io.sb.sb_fwd.resp.valid && !shouldBlock && !fullForward && !io.fu.flush

  io.mem.mem.resp.ready  := (state === LdState.WAIT_MEM || state === LdState.FLUSH_DRAIN) && reqWasCache
  io.mem.mmio.resp.ready := (state === LdState.WAIT_MEM || state === LdState.FLUSH_DRAIN) && !reqWasCache

  private val memReqFire       = io.mem.mem.req.fire || io.mem.mmio.req.fire
  private val memRespFire      = io.mem.mem.resp.fire || io.mem.mmio.resp.fire
  private val memRespData      = Mux(reqWasCache, io.mem.mem.resp.bits.data, io.mem.mmio.resp.bits.data)
  private val expandedFwdMask  = isaImpl.expandByteMask(fwdMaskReg)
  private val mergedBusData    = (memRespData & ~expandedFwdMask) | (fwdDataReg & expandedFwdMask)
  private val fwdResult        = isaImpl.loadResult(ctrlReg, addrReg, fwdResp.data)
  private val memResult        = isaImpl.loadResult(ctrlReg, addrReg, mergedBusData)
  private val memCompleteNow   = state === LdState.WAIT_MEM && memRespFire && !io.fu.flush
  private val doneCompleteNow  = state === LdState.DONE && !io.fu.flush
  private val currentRespValid = fwdCompleteNow || memCompleteNow || doneCompleteNow
  private val currentRespFire  = currentRespValid && io.fu.resp.ready

  io.fu.req.ready := !io.fu.flush && (state === LdState.IDLE || currentRespFire)

  private val acceptFire = io.fu.req.fire && !io.fu.flush

  io.sb.sb_fwd.req.valid       := state === LdState.FWD_REQ && !io.fu.flush
  io.sb.sb_fwd.req.bits.valid  := true.B
  io.sb.sb_fwd.req.bits.sq_seq := sqSeqReg
  io.sb.sb_fwd.req.bits.addr   := alignedAddrReg
  io.sb.sb_fwd.req.bits.mask   := loadMaskReg
  io.sb.sb_fwd.resp.ready      := state === LdState.FWD_RESP && !io.fu.flush

  private val memReqFromRetry = state === LdState.MEM_REQ && !io.fu.flush
  private val memReqFromFwd   = canSendMemFromFwd
  private val memReqActive    = memReqFromRetry || memReqFromFwd

  io.mem.mem.req.valid       := memReqActive && pmaCacheableReg && !io.fu.flush
  io.mem.mem.req.bits.cmd    := CacheCommand.Read
  io.mem.mem.req.bits.addr   := alignedAddrReg
  io.mem.mem.req.bits.data   := 0.U
  io.mem.mem.req.bits.strb   := loadMaskReg
  io.mem.mem.req.bits.source := 0.U

  io.mem.mmio.req.valid       := memReqActive && !pmaCacheableReg && !io.fu.flush
  io.mem.mmio.req.bits.cmd    := CacheCommand.Read
  io.mem.mmio.req.bits.addr   := alignedAddrReg
  io.mem.mmio.req.bits.data   := 0.U
  io.mem.mmio.req.bits.strb   := loadMaskReg
  io.mem.mmio.req.bits.source := 0.U

  private val respResult = Mux(fwdCompleteNow, fwdResult, Mux(memCompleteNow, memResult, resultReg))
  private val resp       = Wire(new FuResp)

  resp.result  := respResult
  resp.rd      := uopReg.rd
  resp.pc      := uopReg.pc
  resp.instr   := uopReg.instr
  resp.rob_tag := uopReg.rob_tag

  io.fu.resp.valid := currentRespValid
  io.fu.resp.bits  := resp

  when(memReqFire || memRespFire) {
    reqOutstanding := (reqOutstanding && !memRespFire) || memReqFire
  }

  when(memReqFire) {
    reqWasCache := pmaCacheableReg
  }

  private val willHaveOutstanding = (reqOutstanding && !memRespFire) || memReqFire

  when(io.fu.flush) {
    when(willHaveOutstanding) {
      state := LdState.FLUSH_DRAIN
    }.otherwise {
      state := LdState.IDLE
    }
  }.otherwise {
    switch(state) {
      is(LdState.IDLE) {}

      is(LdState.FWD_REQ) {
        when(io.sb.sb_fwd.req.fire) {
          state := LdState.FWD_RESP
        }
      }

      is(LdState.FWD_RESP) {
        when(fwdRespFire) {
          when(shouldBlock) {
            state := LdState.FWD_REQ
          }.elsewhen(fullForward) {
            when(io.fu.resp.ready) {
              state := LdState.IDLE
            }.otherwise {
              resultReg := fwdResult
              state     := LdState.DONE
            }
          }.otherwise {
            fwdDataReg := Mux(partialForward, fwdResp.data, 0.U)
            fwdMaskReg := Mux(partialForward, fwdResp.mask, 0.U)

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
          when(io.fu.resp.ready) {
            state := LdState.IDLE
          }.otherwise {
            resultReg := memResult
            state     := LdState.DONE
          }
        }
      }

      is(LdState.DONE) {
        when(io.fu.resp.fire) {
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
      uopReg          := io.fu.req.bits
      sqSeqReg        := io.fu.req.bits.sq_seq
      ctrlReg         := acceptCtrl
      addrReg         := acceptAddr
      alignedAddrReg  := acceptAlignedAddr
      loadMaskReg     := acceptLoadMask
      pmaCacheableReg := acceptPmaCacheable
      resultReg       := 0.U
      fwdDataReg      := 0.U
      fwdMaskReg      := 0.U
      state           := LdState.FWD_REQ
    }
  }
}
