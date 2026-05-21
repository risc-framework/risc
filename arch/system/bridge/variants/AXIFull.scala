package arch.system.bridge

import arch.configs._
import chisel3._
import chisel3.util.{ Cat, log2Ceil, is, switch }
import vamba.axi4.full._
import vcache._

object AXIFullBridgeUtils extends RegisteredUtils[BusBridgeUtils] {
  override def utils: BusBridgeUtils = new BusBridgeUtils {
    override def name: String = "axif"

    private def axiP: Axi4FullParams =
      Axi4FullParams(
        addrWidth = p(XLen),
        dataWidth = p(XLen),
        idWidth = 4,
        userWidth = 0
      )

    override def busType: Bundle =
      new Axi4FullMasterPort(axiP)

    override def createBridge[T <: Data](gen: T, memory: CachePortIO[T], isMmio: Boolean = false): Bundle = {
      val axi = Wire(new Axi4FullMasterPort(axiP))

      val bytesPerGen    = memory.req.bits.data.getWidth / 8
      val axiBeatsPerGen = bytesPerGen / p(BytesPerWord)

      val wordsPerLine  = if (isMmio) 1 else p(L1DCacheLineSize) / bytesPerGen
      val totalAxiBeats = wordsPerLine * axiBeatsPerGen
      val burstLen      = (totalAxiBeats - 1).max(0).U(8.W)

      val state    = RegInit(AXIBridgeState.IDLE)
      val req_addr = RegInit(0.U(p(XLen).W))

      val w_beat_count  = RegInit(0.U(8.W))
      val w_data_buffer = Reg(UInt(memory.req.bits.data.getWidth.W))
      val w_strb_buffer = Reg(UInt(memory.req.bits.strb.getWidth.W))

      axi.aw.valid := false.B
      axi.aw.bits  := DontCare
      axi.w.valid  := false.B
      axi.w.bits   := DontCare
      axi.b.ready  := false.B
      axi.ar.valid := false.B
      axi.ar.bits  := DontCare
      axi.r.ready  := false.B

      memory.req.ready        := false.B
      memory.resp.valid       := false.B
      memory.resp.bits.data   := DontCare
      memory.resp.bits.last   := false.B
      memory.resp.bits.hit    := false.B
      memory.resp.bits.source := 0.U

      switch(state) {
        is(AXIBridgeState.IDLE) {
          memory.req.ready := true.B
          when(memory.req.fire) {
            req_addr := memory.req.bits.addr
            when(memory.req.bits.cmd === CacheCommand.Read) {
              state := AXIBridgeState.AR
            }.otherwise {
              state         := AXIBridgeState.AW
              w_beat_count  := 0.U
              w_data_buffer := memory.req.bits.data.asUInt
              w_strb_buffer := memory.req.bits.strb
            }
          }
        }

        is(AXIBridgeState.AR) {
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
            state := AXIBridgeState.R
          }
        }

        is(AXIBridgeState.R) {
          if (axiBeatsPerGen <= 1) {
            memory.resp.valid     := axi.r.valid
            axi.r.ready           := memory.resp.ready
            memory.resp.bits.data := axi.r.bits.data.asTypeOf(gen)
            memory.resp.bits.last := axi.r.bits.last

            when(axi.r.fire && axi.r.bits.last) {
              state := AXIBridgeState.IDLE
            }
          } else {
            val r_pack_count  = RegInit(0.U(log2Ceil(axiBeatsPerGen).max(1).W))
            val r_data_buffer = Reg(Vec(axiBeatsPerGen, UInt(p(XLen).W)))

            val is_last_pack = r_pack_count === (axiBeatsPerGen - 1).U

            memory.resp.valid := axi.r.valid && is_last_pack
            axi.r.ready       := Mux(is_last_pack, memory.resp.ready, true.B)

            when(axi.r.fire) {
              when(!is_last_pack) {
                r_data_buffer(r_pack_count) := axi.r.bits.data
              }

              r_pack_count := Mux(is_last_pack, 0.U, r_pack_count + 1.U)
            }

            val final_data_vec = Wire(Vec(axiBeatsPerGen, UInt(p(XLen).W)))
            for (i <- 0 until axiBeatsPerGen - 1)
              final_data_vec(i) := r_data_buffer(i)
            final_data_vec(axiBeatsPerGen - 1) := axi.r.bits.data

            memory.resp.bits.data := Cat(final_data_vec.reverse).asTypeOf(gen)
            memory.resp.bits.last := axi.r.bits.last

            when(axi.r.fire && axi.r.bits.last) {
              state := AXIBridgeState.IDLE
            }
          }
        }

        is(AXIBridgeState.AW) {
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
            state := AXIBridgeState.W
          }
        }

        is(AXIBridgeState.W) {
          if (axiBeatsPerGen <= 1) {
            val is_first = w_beat_count === 0.U

            axi.w.valid      := is_first || memory.req.valid
            memory.req.ready := !is_first && axi.w.ready

            axi.w.bits.data := Mux(is_first, w_data_buffer, memory.req.bits.data.asUInt)
            axi.w.bits.strb := Mux(is_first, w_strb_buffer, memory.req.bits.strb)
            axi.w.bits.last := w_beat_count === burstLen
            axi.w.bits.user := 0.U

            when(axi.w.fire) {
              w_beat_count := w_beat_count + 1.U
              when(w_beat_count === burstLen) {
                state := AXIBridgeState.B
              }
            }
          } else {
            val w_unpack_count = RegInit(0.U(log2Ceil(axiBeatsPerGen).max(1).W))

            axi.w.valid     := true.B
            axi.w.bits.data := w_data_buffer(p(XLen) - 1, 0)
            axi.w.bits.strb := w_strb_buffer(p(BytesPerWord) - 1, 0)
            axi.w.bits.last := w_beat_count === burstLen
            axi.w.bits.user := 0.U

            val is_last_unpack = w_unpack_count === (axiBeatsPerGen - 1).U
            memory.req.ready := axi.w.ready && is_last_unpack && (w_beat_count =/= burstLen)

            when(axi.w.fire) {
              w_beat_count   := w_beat_count + 1.U
              w_unpack_count := Mux(is_last_unpack, 0.U, w_unpack_count + 1.U)

              when(is_last_unpack) {
                w_data_buffer := memory.req.bits.data.asUInt
                w_strb_buffer := memory.req.bits.strb
              }.otherwise {
                w_data_buffer := w_data_buffer >> p(XLen)
                w_strb_buffer := w_strb_buffer >> p(BytesPerWord)
              }

              when(w_beat_count === burstLen) {
                state := AXIBridgeState.B
              }
            }
          }
        }

        is(AXIBridgeState.B) {
          val bDone = RegInit(false.B)

          axi.b.ready           := !bDone
          memory.resp.valid     := bDone || axi.b.valid
          memory.resp.bits.data := 0.U.asTypeOf(gen)
          memory.resp.bits.last := true.B
          memory.resp.bits.hit  := false.B

          when(axi.b.fire) {
            bDone := true.B
          }

          when(memory.resp.fire) {
            bDone := false.B
            state := AXIBridgeState.IDLE
          }
        }
      }

      axi
    }

    override def createBridgeReadOnly[T <: Data](gen: T, memory: CachePortIO[T], isMmio: Boolean = false): Bundle = {
      val axi = Wire(new Axi4FullMasterPort(axiP))

      val bytesPerGen    = memory.resp.bits.data.getWidth / 8
      val axiBeatsPerGen = bytesPerGen / p(BytesPerWord)

      val wordsPerLine  = if (isMmio) 1 else p(L1ICacheLineSize) / bytesPerGen
      val totalAxiBeats = wordsPerLine * axiBeatsPerGen
      val burstLen      = (totalAxiBeats - 1).max(0).U(8.W)

      val state    = RegInit(AXIBridgeState.IDLE)
      val req_addr = RegInit(0.U(p(XLen).W))

      axi.aw.valid := false.B
      axi.aw.bits  := DontCare
      axi.w.valid  := false.B
      axi.w.bits   := DontCare
      axi.b.ready  := false.B
      axi.ar.valid := false.B
      axi.ar.bits  := DontCare
      axi.r.ready  := false.B

      memory.req.ready        := false.B
      memory.resp.valid       := false.B
      memory.resp.bits.data   := DontCare
      memory.resp.bits.last   := false.B
      memory.resp.bits.hit    := false.B
      memory.resp.bits.source := 0.U

      switch(state) {
        is(AXIBridgeState.IDLE) {
          memory.req.ready := true.B
          when(memory.req.fire) {
            req_addr := memory.req.bits.addr
            state    := AXIBridgeState.AR
          }
        }

        is(AXIBridgeState.AR) {
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
            state := AXIBridgeState.R
          }
        }

        is(AXIBridgeState.R) {
          if (axiBeatsPerGen <= 1) {
            memory.resp.valid     := axi.r.valid
            axi.r.ready           := memory.resp.ready
            memory.resp.bits.data := axi.r.bits.data.asTypeOf(gen)
            memory.resp.bits.last := axi.r.bits.last

            when(axi.r.fire && axi.r.bits.last) {
              state := AXIBridgeState.IDLE
            }
          } else {
            val r_pack_count  = RegInit(0.U(log2Ceil(axiBeatsPerGen).max(1).W))
            val r_data_buffer = Reg(Vec(axiBeatsPerGen, UInt(p(XLen).W)))

            val is_last_pack = r_pack_count === (axiBeatsPerGen - 1).U

            memory.resp.valid := axi.r.valid && is_last_pack
            axi.r.ready       := Mux(is_last_pack, memory.resp.ready, true.B)

            when(axi.r.fire) {
              when(!is_last_pack) {
                r_data_buffer(r_pack_count) := axi.r.bits.data
              }

              r_pack_count := Mux(is_last_pack, 0.U, r_pack_count + 1.U)
            }

            val final_data_vec = Wire(Vec(axiBeatsPerGen, UInt(p(XLen).W)))
            for (i <- 0 until axiBeatsPerGen - 1)
              final_data_vec(i) := r_data_buffer(i)
            final_data_vec(axiBeatsPerGen - 1) := axi.r.bits.data

            memory.resp.bits.data := Cat(final_data_vec.reverse).asTypeOf(gen)
            memory.resp.bits.last := axi.r.bits.last

            when(axi.r.fire && axi.r.bits.last) {
              state := AXIBridgeState.IDLE
            }
          }
        }
      }

      axi
    }
  }

  override def factory: UtilsFactory[BusBridgeUtils] =
    BusBridgeUtilsFactory
}
