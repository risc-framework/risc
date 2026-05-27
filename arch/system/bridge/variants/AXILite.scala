package arch.system.bridge

import arch.configs._
import vamba.axi4.lite.{ Axi4LiteMasterPort, Axi4LiteParams }
import vcache.{ CachePortIO, CacheCommand }
import chisel3._
import chisel3.util.{ Cat, log2Ceil, is, switch }

object AXIBridgeState extends ChiselEnum { val Idle, AR, R, AW, W, B = Value }

object AXILiteBridgeUtils extends RegisteredUtils[BusBridgeUtils] {
  override def utils: BusBridgeUtils = new BusBridgeUtils {
    override def name: String = "axil"

    private def axiP: Axi4LiteParams =
      Axi4LiteParams(addrWidth = p(XLen), dataWidth = p(XLen))

    override def busType: Bundle =
      new Axi4LiteMasterPort(axiP)

    override def createBridge[T <: Data](gen: T, memory: CachePortIO[T], isMmio: Boolean = false): Bundle = {
      val axi = Wire(new Axi4LiteMasterPort(axiP))

      val beats = (gen.getWidth / p(XLen)).max(1)

      val state    = RegInit(AXIBridgeState.Idle)
      val req_addr = RegInit(0.U(p(XLen).W))
      val req_cmd  = RegInit(CacheCommand.Read)

      val w_data = Reg(UInt(gen.getWidth.max(p(XLen)).W))
      val w_strb = Reg(UInt((gen.getWidth / 8).max(p(BytesPerWord)).W))
      val r_data = Reg(Vec(beats, UInt(p(XLen).W)))

      val beat = RegInit(0.U(log2Ceil(beats + 1).max(1).W))

      axi.ar.valid := false.B
      axi.ar.bits  := DontCare
      axi.r.ready  := false.B
      axi.aw.valid := false.B
      axi.aw.bits  := DontCare
      axi.w.valid  := false.B
      axi.w.bits   := DontCare
      axi.b.ready  := false.B

      memory.req.ready        := false.B
      memory.resp.valid       := false.B
      memory.resp.bits.data   := DontCare
      memory.resp.bits.hit    := false.B
      memory.resp.bits.last   := true.B
      memory.resp.bits.source := 0.U

      switch(state) {
        is(AXIBridgeState.Idle) {
          memory.req.ready := true.B
          when(memory.req.fire) {
            req_addr := memory.req.bits.addr
            req_cmd  := memory.req.bits.cmd
            w_data   := memory.req.bits.data.asUInt
            w_strb   := memory.req.bits.strb
            beat     := 0.U
            state    := Mux(memory.req.bits.cmd === CacheCommand.Read, AXIBridgeState.AR, AXIBridgeState.AW)
          }
        }

        is(AXIBridgeState.AR) {
          axi.ar.valid     := true.B
          axi.ar.bits.addr := req_addr + (beat * p(BytesPerWord).U)
          axi.ar.bits.prot := 0.U
          when(axi.ar.fire) {
            state := AXIBridgeState.R
          }
        }

        is(AXIBridgeState.R) {
          val is_last = beat === (beats - 1).U

          memory.resp.valid := axi.r.valid && is_last
          axi.r.ready       := Mux(is_last, memory.resp.ready, true.B)

          val final_data = Wire(Vec(beats, UInt(p(XLen).W)))
          for (i <- 0 until beats)
            final_data(i) := Mux(i.U === beat, axi.r.bits.data, r_data(i))

          memory.resp.bits.data := final_data.asUInt.asTypeOf(gen)

          when(axi.r.fire) {
            r_data(beat) := axi.r.bits.data
            beat         := beat + 1.U
            state        := Mux(is_last, AXIBridgeState.Idle, AXIBridgeState.AR)
          }
        }

        is(AXIBridgeState.AW) {
          axi.aw.valid     := true.B
          axi.aw.bits.addr := req_addr + (beat * p(BytesPerWord).U)
          axi.aw.bits.prot := 0.U
          when(axi.aw.fire) {
            state := AXIBridgeState.W
          }
        }

        is(AXIBridgeState.W) {
          axi.w.valid     := true.B
          axi.w.bits.data := w_data(p(XLen) - 1, 0)
          axi.w.bits.strb := w_strb(p(BytesPerWord) - 1, 0)

          when(axi.w.fire) {
            w_data := w_data >> p(XLen)
            w_strb := w_strb >> p(BytesPerWord)
            state  := AXIBridgeState.B
          }
        }

        is(AXIBridgeState.B) {
          val is_last = beat === (beats - 1).U

          memory.resp.valid := axi.b.valid && is_last
          axi.b.ready       := Mux(is_last, memory.resp.ready, true.B)

          when(axi.b.fire) {
            beat  := beat + 1.U
            state := Mux(is_last, AXIBridgeState.Idle, AXIBridgeState.AW)
          }
        }
      }

      axi
    }

    override def createBridgeReadOnly[T <: Data](gen: T, memory: CachePortIO[T], isMmio: Boolean = false): Bundle = {
      val axi = Wire(new Axi4LiteMasterPort(axiP))

      val bytesPerGen    = memory.resp.bits.data.getWidth / 8
      val axiBeatsPerGen = bytesPerGen / p(BytesPerWord)

      val wordsPerLine  = if (isMmio) 1 else p(L1ICacheLineSize) / bytesPerGen
      val totalAxiBeats = wordsPerLine * axiBeatsPerGen

      val state    = RegInit(AXIBridgeState.Idle)
      val req_addr = RegInit(0.U(p(XLen).W))

      val r_beat_count  = RegInit(0.U(log2Ceil(totalAxiBeats + 1).max(1).W))
      val r_pack_count  = RegInit(0.U(log2Ceil(axiBeatsPerGen + 1).max(1).W))
      val r_data_buffer = Reg(Vec(axiBeatsPerGen.max(1), UInt(p(XLen).W)))

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
        is(AXIBridgeState.Idle) {
          memory.req.ready := true.B
          when(memory.req.fire) {
            req_addr     := memory.req.bits.addr
            state        := AXIBridgeState.AR
            r_beat_count := 0.U
            r_pack_count := 0.U
          }
        }

        is(AXIBridgeState.AR) {
          val ar_addr = req_addr + (r_beat_count * p(BytesPerWord).U)

          axi.ar.valid     := true.B
          axi.ar.bits.addr := ar_addr
          axi.ar.bits.prot := 0.U

          when(axi.ar.fire) {
            state := AXIBridgeState.R
          }
        }

        is(AXIBridgeState.R) {
          val is_last_pack = r_pack_count === (axiBeatsPerGen - 1).U
          val is_last_beat = r_beat_count === (totalAxiBeats - 1).U

          memory.resp.valid     := axi.r.valid && is_last_pack
          memory.resp.bits.last := is_last_beat

          val final_data_vec = Wire(Vec(axiBeatsPerGen, UInt(p(XLen).W)))
          for (i <- 0 until axiBeatsPerGen)
            final_data_vec(i) := Mux(i.U === r_pack_count, axi.r.bits.data, r_data_buffer(i))

          memory.resp.bits.data := Cat(final_data_vec.reverse).asTypeOf(gen)

          axi.r.ready := Mux(is_last_pack, memory.resp.ready, true.B)

          when(axi.r.fire) {
            when(!is_last_pack) {
              r_data_buffer(r_pack_count) := axi.r.bits.data
            }

            r_pack_count := Mux(is_last_pack, 0.U, r_pack_count + 1.U)
            r_beat_count := r_beat_count + 1.U

            when(is_last_beat) {
              state := AXIBridgeState.Idle
            }.otherwise {
              state := AXIBridgeState.AR
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
