package arch.system.bridge.impls.bus.axil

import arch.configs._
import arch.system.bridge._
import vamba.axi4.lite.{ Axi4LiteMasterPort, Axi4LiteParams }
import vcache.{ CacheCommand, CachePortIO }
import chisel3._
import chisel3.util.{ Cat, is, log2Ceil, switch }
import vutils.graph.{ NodeRegistry, RegisteredNodeUtils }

object BusBridgeAxilType extends RegisteredNodeUtils[BusBridgeTypeImpl] {
  override def utils: BusBridgeTypeImpl = new BusBridgeTypeImpl {
    override def value: String = "axil"

    private def axiP(implicit p: Parameters): Axi4LiteParams =
      Axi4LiteParams(addrWidth = p(XLen), dataWidth = p(XLen))

    override def busType(implicit p: Parameters): Bundle =
      new Axi4LiteMasterPort(axiP)

    override def createBridge[T <: Data](
      gen: T,
      memory: CachePortIO[T],
      isMmio: Boolean = false
    )(implicit p: Parameters): Bundle = {
      val axi = Wire(new Axi4LiteMasterPort(axiP))

      val beats = (gen.getWidth / p(XLen)).max(1)

      val state    = RegInit(Axi4BridgeState.Idle)
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
        is(Axi4BridgeState.Idle) {
          memory.req.ready := true.B

          when(memory.req.fire) {
            req_addr := memory.req.bits.addr
            req_cmd  := memory.req.bits.cmd
            w_data   := memory.req.bits.data.asUInt
            w_strb   := memory.req.bits.strb
            beat     := 0.U
            state    := Mux(
              memory.req.bits.cmd === CacheCommand.Read,
              Axi4BridgeState.AR,
              Axi4BridgeState.AW
            )
          }
        }

        is(Axi4BridgeState.AR) {
          axi.ar.valid     := true.B
          axi.ar.bits.addr := req_addr + (beat * p(BytesPerWord).U)
          axi.ar.bits.prot := 0.U

          when(axi.ar.fire) {
            state := Axi4BridgeState.R
          }
        }

        is(Axi4BridgeState.R) {
          val isLast = beat === (beats - 1).U

          memory.resp.valid := axi.r.valid && isLast
          axi.r.ready       := Mux(isLast, memory.resp.ready, true.B)

          val finalData = Wire(Vec(beats, UInt(p(XLen).W)))

          for (i <- 0 until beats)
            finalData(i) := Mux(i.U === beat, axi.r.bits.data, r_data(i))

          memory.resp.bits.data := finalData.asUInt.asTypeOf(gen)

          when(axi.r.fire) {
            r_data(beat) := axi.r.bits.data
            beat         := beat + 1.U
            state        := Mux(isLast, Axi4BridgeState.Idle, Axi4BridgeState.AR)
          }
        }

        is(Axi4BridgeState.AW) {
          axi.aw.valid     := true.B
          axi.aw.bits.addr := req_addr + (beat * p(BytesPerWord).U)
          axi.aw.bits.prot := 0.U

          when(axi.aw.fire) {
            state := Axi4BridgeState.W
          }
        }

        is(Axi4BridgeState.W) {
          axi.w.valid     := true.B
          axi.w.bits.data := w_data(p(XLen) - 1, 0)
          axi.w.bits.strb := w_strb(p(BytesPerWord) - 1, 0)

          when(axi.w.fire) {
            w_data := w_data >> p(XLen)
            w_strb := w_strb >> p(BytesPerWord)
            state  := Axi4BridgeState.B
          }
        }

        is(Axi4BridgeState.B) {
          val isLast = beat === (beats - 1).U

          memory.resp.valid := axi.b.valid && isLast
          axi.b.ready       := Mux(isLast, memory.resp.ready, true.B)

          when(axi.b.fire) {
            beat  := beat + 1.U
            state := Mux(isLast, Axi4BridgeState.Idle, Axi4BridgeState.AW)
          }
        }
      }

      axi
    }

    override def createBridgeReadOnly[T <: Data](
      gen: T,
      memory: CachePortIO[T],
      isMmio: Boolean = false
    )(implicit p: Parameters): Bundle = {
      val axi = Wire(new Axi4LiteMasterPort(axiP))

      val bytesPerGen    = memory.resp.bits.data.getWidth / 8
      val axiBeatsPerGen = bytesPerGen / p(BytesPerWord)

      val wordsPerLine  = if (isMmio) 1 else p(L1ICacheLineSize) / bytesPerGen
      val totalAxiBeats = wordsPerLine * axiBeatsPerGen

      val state    = RegInit(Axi4BridgeState.Idle)
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
        is(Axi4BridgeState.Idle) {
          memory.req.ready := true.B

          when(memory.req.fire) {
            req_addr     := memory.req.bits.addr
            state        := Axi4BridgeState.AR
            r_beat_count := 0.U
            r_pack_count := 0.U
          }
        }

        is(Axi4BridgeState.AR) {
          val arAddr = req_addr + (r_beat_count * p(BytesPerWord).U)

          axi.ar.valid     := true.B
          axi.ar.bits.addr := arAddr
          axi.ar.bits.prot := 0.U

          when(axi.ar.fire) {
            state := Axi4BridgeState.R
          }
        }

        is(Axi4BridgeState.R) {
          val isLastPack = r_pack_count === (axiBeatsPerGen - 1).U
          val isLastBeat = r_beat_count === (totalAxiBeats - 1).U

          memory.resp.valid     := axi.r.valid && isLastPack
          memory.resp.bits.last := isLastBeat

          val finalDataVec = Wire(Vec(axiBeatsPerGen, UInt(p(XLen).W)))

          for (i <- 0 until axiBeatsPerGen)
            finalDataVec(i) := Mux(i.U === r_pack_count, axi.r.bits.data, r_data_buffer(i))

          memory.resp.bits.data := Cat(finalDataVec.reverse).asTypeOf(gen)

          axi.r.ready := Mux(isLastPack, memory.resp.ready, true.B)

          when(axi.r.fire) {
            when(!isLastPack) {
              r_data_buffer(r_pack_count) := axi.r.bits.data
            }

            r_pack_count := Mux(isLastPack, 0.U, r_pack_count + 1.U)
            r_beat_count := r_beat_count + 1.U

            when(isLastBeat) {
              state := Axi4BridgeState.Idle
            }.otherwise {
              state := Axi4BridgeState.AR
            }
          }
        }
      }

      axi
    }
  }

  override def registry: NodeRegistry[BusBridgeTypeImpl] = BusBridgeTypeFactory
}
