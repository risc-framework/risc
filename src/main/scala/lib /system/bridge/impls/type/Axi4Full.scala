package arch.system.bridge.impls.bus.axif

import arch.configs._
import arch.system.bridge._
import vamba.axi4.full.{ Axi4FullBurst, Axi4FullMasterPort, Axi4FullParams }
import vcache.{ CacheCommand, CacheReq, CacheReadReq, CacheResp }
import chisel3._
import chisel3.util.{ Cat, is, log2Ceil, switch, DecoupledIO }
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }

object BusBridgeAxifType extends RegisteredNodeUtils[BusBridgeTypeImpl] {
  override def utils: BusBridgeTypeImpl = new BusBridgeTypeImpl {
    override def value: String = "axif"

    private def axiP(implicit p: Parameters): Axi4FullParams =
      Axi4FullParams(
        addrWidth = p(XLen),
        dataWidth = p(XLen),
        idWidth = 4,
        userWidth = 0
      )

    override def busType(implicit p: Parameters): Bundle =
      new Axi4FullMasterPort(axiP)

    override def createBridge[T <: Data](
      gen: T,
      req: DecoupledIO[CacheReq[T]],
      resp: DecoupledIO[CacheResp[T]],
      isMmio: Boolean = false
    )(implicit p: Parameters): Bundle = {
      val axi = Wire(new Axi4FullMasterPort(axiP))

      val bytesPerGen    = req.bits.data.getWidth / 8
      val axiBeatsPerGen = bytesPerGen / p(BytesPerWord)

      val wordsPerLine  = if (isMmio) 1 else p(L1DCacheLineSize) / bytesPerGen
      val totalAxiBeats = wordsPerLine * axiBeatsPerGen
      val burstLen      = (totalAxiBeats - 1).max(0).U(8.W)

      val state    = RegInit(Axi4BridgeState.Idle)
      val req_addr = RegInit(0.U(p(XLen).W))

      val w_beat_count  = RegInit(0.U(8.W))
      val w_data_buffer = Reg(UInt(req.bits.data.getWidth.W))
      val w_strb_buffer = Reg(UInt(req.bits.strb.getWidth.W))

      axi.aw.valid := false.B
      axi.aw.bits  := DontCare
      axi.w.valid  := false.B
      axi.w.bits   := DontCare
      axi.b.ready  := false.B
      axi.ar.valid := false.B
      axi.ar.bits  := DontCare
      axi.r.ready  := false.B

      req.ready      := false.B
      resp.valid     := false.B
      resp.bits.data := DontCare
      resp.bits.last := false.B
      resp.bits.hit  := false.B

      switch(state) {
        is(Axi4BridgeState.Idle) {
          req.ready := true.B

          when(req.fire) {
            req_addr := req.bits.addr

            when(req.bits.cmd === CacheCommand.Read) {
              state := Axi4BridgeState.AR
            }.otherwise {
              state         := Axi4BridgeState.AW
              w_beat_count  := 0.U
              w_data_buffer := req.bits.data.asUInt
              w_strb_buffer := req.bits.strb
            }
          }
        }

        is(Axi4BridgeState.AR) {
          axi.ar.valid       := true.B
          axi.ar.bits.addr   := req_addr
          axi.ar.bits.prot   := 0.U
          axi.ar.bits.id     := 0.U
          axi.ar.bits.len    := burstLen
          axi.ar.bits.size   := log2Ceil(p(BytesPerWord)).U
          axi.ar.bits.burst  := Mux(isMmio.B, Axi4FullBurst.INCR, Axi4FullBurst.WRAP)
          axi.ar.bits.lock   := false.B
          axi.ar.bits.cache  := 0.U
          axi.ar.bits.qos    := 0.U
          axi.ar.bits.region := 0.U
          axi.ar.bits.user   := 0.U

          when(axi.ar.fire) {
            state := Axi4BridgeState.R
          }
        }

        is(Axi4BridgeState.R) {
          if (axiBeatsPerGen <= 1) {
            resp.valid     := axi.r.valid
            axi.r.ready    := resp.ready
            resp.bits.data := axi.r.bits.data.asTypeOf(gen)
            resp.bits.last := axi.r.bits.last

            when(axi.r.fire && axi.r.bits.last) {
              state := Axi4BridgeState.Idle
            }
          } else {
            val r_pack_count  = RegInit(0.U(log2Ceil(axiBeatsPerGen).max(1).W))
            val r_data_buffer = Reg(Vec(axiBeatsPerGen, UInt(p(XLen).W)))

            val isLastPack = r_pack_count === (axiBeatsPerGen - 1).U

            resp.valid  := axi.r.valid && isLastPack
            axi.r.ready := Mux(isLastPack, resp.ready, true.B)

            when(axi.r.fire) {
              when(!isLastPack) {
                r_data_buffer(r_pack_count) := axi.r.bits.data
              }

              r_pack_count := Mux(isLastPack, 0.U, r_pack_count + 1.U)
            }

            val finalDataVec = Wire(Vec(axiBeatsPerGen, UInt(p(XLen).W)))

            for (i <- 0 until axiBeatsPerGen - 1)
              finalDataVec(i) := r_data_buffer(i)

            finalDataVec(axiBeatsPerGen - 1) := axi.r.bits.data

            resp.bits.data := Cat(finalDataVec.reverse).asTypeOf(gen)
            resp.bits.last := axi.r.bits.last

            when(axi.r.fire && axi.r.bits.last) {
              state := Axi4BridgeState.Idle
            }
          }
        }

        is(Axi4BridgeState.AW) {
          axi.aw.valid       := true.B
          axi.aw.bits.addr   := req_addr
          axi.aw.bits.prot   := 0.U
          axi.aw.bits.id     := 0.U
          axi.aw.bits.len    := burstLen
          axi.aw.bits.size   := log2Ceil(p(BytesPerWord)).U
          axi.aw.bits.burst  := Axi4FullBurst.INCR
          axi.aw.bits.lock   := false.B
          axi.aw.bits.cache  := 0.U
          axi.aw.bits.qos    := 0.U
          axi.aw.bits.region := 0.U
          axi.aw.bits.user   := 0.U

          when(axi.aw.fire) {
            state := Axi4BridgeState.W
          }
        }

        is(Axi4BridgeState.W) {
          if (axiBeatsPerGen <= 1) {
            val isFirst = w_beat_count === 0.U

            axi.w.valid := isFirst || req.valid
            req.ready   := !isFirst && axi.w.ready

            axi.w.bits.data := Mux(isFirst, w_data_buffer, req.bits.data.asUInt)
            axi.w.bits.strb := Mux(isFirst, w_strb_buffer, req.bits.strb)
            axi.w.bits.last := w_beat_count === burstLen
            axi.w.bits.user := 0.U

            when(axi.w.fire) {
              w_beat_count := w_beat_count + 1.U

              when(w_beat_count === burstLen) {
                state := Axi4BridgeState.B
              }
            }
          } else {
            val w_unpack_count = RegInit(0.U(log2Ceil(axiBeatsPerGen).max(1).W))

            axi.w.valid     := true.B
            axi.w.bits.data := w_data_buffer(p(XLen) - 1, 0)
            axi.w.bits.strb := w_strb_buffer(p(BytesPerWord) - 1, 0)
            axi.w.bits.last := w_beat_count === burstLen
            axi.w.bits.user := 0.U

            val isLastUnpack = w_unpack_count === (axiBeatsPerGen - 1).U

            req.ready := axi.w.ready && isLastUnpack && (w_beat_count =/= burstLen)

            when(axi.w.fire) {
              w_beat_count   := w_beat_count + 1.U
              w_unpack_count := Mux(isLastUnpack, 0.U, w_unpack_count + 1.U)

              when(isLastUnpack) {
                w_data_buffer := req.bits.data.asUInt
                w_strb_buffer := req.bits.strb
              }.otherwise {
                w_data_buffer := w_data_buffer >> p(XLen)
                w_strb_buffer := w_strb_buffer >> p(BytesPerWord)
              }

              when(w_beat_count === burstLen) {
                state := Axi4BridgeState.B
              }
            }
          }
        }

        is(Axi4BridgeState.B) {
          val bDone = RegInit(false.B)

          axi.b.ready    := !bDone
          resp.valid     := bDone || axi.b.valid
          resp.bits.data := 0.U.asTypeOf(gen)
          resp.bits.last := true.B
          resp.bits.hit  := false.B

          when(axi.b.fire) {
            bDone := true.B
          }

          when(resp.fire) {
            bDone := false.B
            state := Axi4BridgeState.Idle
          }
        }
      }

      axi
    }

    override def createBridgeReadOnly[T <: Data](
      gen: T,
      req: DecoupledIO[CacheReadReq],
      resp: DecoupledIO[CacheResp[T]],
      isMmio: Boolean = false
    )(implicit p: Parameters): Bundle = {
      val axi = Wire(new Axi4FullMasterPort(axiP))

      val bytesPerGen    = resp.bits.data.getWidth / 8
      val axiBeatsPerGen = bytesPerGen / p(BytesPerWord)

      val wordsPerLine  = if (isMmio) 1 else p(L1ICacheLineSize) / bytesPerGen
      val totalAxiBeats = wordsPerLine * axiBeatsPerGen
      val burstLen      = (totalAxiBeats - 1).max(0).U(8.W)

      val state    = RegInit(Axi4BridgeState.Idle)
      val req_addr = RegInit(0.U(p(XLen).W))

      axi.aw.valid := false.B
      axi.aw.bits  := DontCare
      axi.w.valid  := false.B
      axi.w.bits   := DontCare
      axi.b.ready  := false.B
      axi.ar.valid := false.B
      axi.ar.bits  := DontCare
      axi.r.ready  := false.B

      req.ready      := false.B
      resp.valid     := false.B
      resp.bits.data := DontCare
      resp.bits.last := false.B
      resp.bits.hit  := false.B

      switch(state) {
        is(Axi4BridgeState.Idle) {
          req.ready := true.B

          when(req.fire) {
            req_addr := req.bits.addr
            state    := Axi4BridgeState.AR
          }
        }

        is(Axi4BridgeState.AR) {
          axi.ar.valid       := true.B
          axi.ar.bits.addr   := req_addr
          axi.ar.bits.prot   := 0.U
          axi.ar.bits.id     := 0.U
          axi.ar.bits.len    := burstLen
          axi.ar.bits.size   := log2Ceil(p(BytesPerWord)).U
          axi.ar.bits.burst  := Mux(isMmio.B, Axi4FullBurst.INCR, Axi4FullBurst.WRAP)
          axi.ar.bits.lock   := false.B
          axi.ar.bits.cache  := 0.U
          axi.ar.bits.qos    := 0.U
          axi.ar.bits.region := 0.U
          axi.ar.bits.user   := 0.U

          when(axi.ar.fire) {
            state := Axi4BridgeState.R
          }
        }

        is(Axi4BridgeState.R) {
          if (axiBeatsPerGen <= 1) {
            resp.valid     := axi.r.valid
            axi.r.ready    := resp.ready
            resp.bits.data := axi.r.bits.data.asTypeOf(gen)
            resp.bits.last := axi.r.bits.last

            when(axi.r.fire && axi.r.bits.last) {
              state := Axi4BridgeState.Idle
            }
          } else {
            val r_pack_count  = RegInit(0.U(log2Ceil(axiBeatsPerGen).max(1).W))
            val r_data_buffer = Reg(Vec(axiBeatsPerGen, UInt(p(XLen).W)))

            val isLastPack = r_pack_count === (axiBeatsPerGen - 1).U

            resp.valid  := axi.r.valid && isLastPack
            axi.r.ready := Mux(isLastPack, resp.ready, true.B)

            when(axi.r.fire) {
              when(!isLastPack) {
                r_data_buffer(r_pack_count) := axi.r.bits.data
              }

              r_pack_count := Mux(isLastPack, 0.U, r_pack_count + 1.U)
            }

            val finalDataVec = Wire(Vec(axiBeatsPerGen, UInt(p(XLen).W)))

            for (i <- 0 until axiBeatsPerGen - 1)
              finalDataVec(i) := r_data_buffer(i)

            finalDataVec(axiBeatsPerGen - 1) := axi.r.bits.data

            resp.bits.data := Cat(finalDataVec.reverse).asTypeOf(gen)
            resp.bits.last := axi.r.bits.last

            when(axi.r.fire && axi.r.bits.last) {
              state := Axi4BridgeState.Idle
            }
          }
        }
      }

      axi
    }
  }

  override def registry: NodeDimensionRegistry[BusBridgeTypeImpl] =
    BusBridgeTypeFactory
}
