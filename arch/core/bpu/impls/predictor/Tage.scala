package arch.core.bpu.impls.predictor

import arch.core.bpu._
import arch.configs._
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.{ Cat, UIntToOH, isPow2, log2Ceil }

object TagePredictor extends RegisteredNodeUtils[PredictorKindImpl] with BHTConsts {
  override def utils: PredictorKindImpl = new PredictorKindImpl with BHTConsts {
    override def value: String = "tage"

    override def elaborate(req: PredictorQueryReq, resp: PredictorQueryResp, update: BpuUpdate)(
      implicit p: Parameters
    ): Unit = {
      val tableEntries  = p(TageTableEntries)
      val historyLengths = p(TageHistoryLengths)
      val tagWidths     = p(TageTagWidths)
      val tableCount    = historyLengths.length
      val counterWidth  = p(TageCounterWidth)
      val usefulWidth   = p(TageUsefulWidth)
      val historyWidth  = p(BpuHistoryWidth)
      val providerWidth = p(TageProviderWidth)
      val baseEntries   = 1 << p(GShareGhrWidth)
      val numReadPorts  = p(IssueWidth) + 1

      require(tableCount > 0, "TAGE requires at least one tagged table")
      require(tableEntries.length == tableCount, "TAGE entry/history table counts must match")
      require(tagWidths.length == tableCount, "TAGE tag/history table counts must match")
      require(historyLengths == historyLengths.sorted.distinct, "TAGE histories must be unique and increasing")
      require(historyLengths.forall(h => h >= 2 && h <= historyWidth), "Invalid TAGE history length")
      require(tableEntries.forall(entries => isPow2(entries)), "TAGE table sizes must be powers of two")
      require(tagWidths.forall(_ >= 2), "TAGE tags must be at least 2 bits")
      require(counterWidth >= 2, "TAGE counters must be at least 2 bits")
      require(usefulWidth >= 1, "TAGE usefulness counters must be at least 1 bit")
      require(p(GShareGhrWidth) >= 2, "GShareGhrWidth must be at least 2")

      val commitGhr = RegInit(0.U(historyWidth.W))
      val specGhr   = RegInit(0.U(historyWidth.W))
      val basePht   = RegInit(VecInit(Seq.fill(baseEntries)(BHT_WT.value.U(SZ_BHT.W))))
      val tableValid = tableEntries.map(entries => RegInit(0.U(entries.W)))
      val tables = tableEntries.zip(tagWidths).map { case (entries, tagWidth) =>
        val dataWidth = tagWidth + counterWidth + usefulWidth
        Seq.fill(numReadPorts)(Mem(entries, UInt(dataWidth.W)))
      }

      def entryTag(data: UInt, table: Int): UInt = {
        val dataWidth = tagWidths(table) + counterWidth + usefulWidth
        data(dataWidth - 1, counterWidth + usefulWidth)
      }

      def entryCounter(data: UInt): UInt =
        data(counterWidth + usefulWidth - 1, usefulWidth)

      def entryUseful(data: UInt): UInt =
        data(usefulWidth - 1, 0)

      def packEntry(tag: UInt, counter: UInt, useful: UInt): UInt =
        Cat(tag, counter, useful)

      def zeroExtend(bits: UInt, width: Int): UInt = {
        val bitWidth = bits.getWidth
        if (bitWidth == width) bits else Cat(0.U((width - bitWidth).W), bits)
      }

      def fold(value: UInt, usedWidth: Int, outWidth: Int): UInt = {
        val chunks = (0 until usedWidth by outWidth).map { lo =>
          val hi = (lo + outWidth - 1).min(usedWidth - 1)
          zeroExtend(value(hi, lo), outWidth)
        }
        chunks.reduce(_ ^ _)
      }

      def foldPc(pc: UInt, outWidth: Int): UInt = {
        val chunks = (p(PCAlign) until p(XLen) by outWidth).map { lo =>
          val hi = (lo + outWidth - 1).min(p(XLen) - 1)
          zeroExtend(pc(hi, lo), outWidth)
        }
        chunks.reduce(_ ^ _)
      }

      def baseIndex(pc: UInt, hist: UInt): UInt =
        foldPc(pc, p(GShareGhrWidth)) ^ hist(p(GShareGhrWidth) - 1, 0)

      def tableIndex(pc: UInt, hist: UInt, table: Int): UInt = {
        val indexWidth = log2Ceil(tableEntries(table))
        foldPc(pc, indexWidth) ^ fold(hist, historyLengths(table), indexWidth)
      }

      def tableTag(pc: UInt, hist: UInt, table: Int): UInt = {
        val tagWidth  = tagWidths(table)
        val histFold  = fold(hist, historyLengths(table), tagWidth)
        val histRotate = Cat(histFold(tagWidth - 2, 0), histFold(tagWidth - 1))
        foldPc(pc, tagWidth) ^ histFold ^ histRotate
      }

      def shiftHist(hist: UInt, taken: Bool): UInt =
        Cat(hist(historyWidth - 2, 0), taken)

      def satUpdate(counter: UInt, taken: Bool, width: Int): UInt = {
        val max = ((BigInt(1) << width) - 1).U(width.W)
        Mux(taken, Mux(counter === max, max, counter + 1.U), Mux(counter === 0.U, 0.U, counter - 1.U))
      }

      def updateUseful(old: UInt, increment: Bool): UInt =
        satUpdate(old, increment, usefulWidth)

      val baseOldCounter = basePht(update.pht_index)
      val baseNewCounter = satUpdate(baseOldCounter, update.taken, SZ_BHT)
      val updateNextGhr  = shiftHist(update.ghr_snapshot, update.taken)

      val queryGhr = Wire(Vec(p(IssueWidth) + 1, UInt(historyWidth.W)))
      queryGhr(0) := specGhr

      for (w <- 0 until p(IssueWidth)) {
        val baseIdx     = baseIndex(req.pc(w), queryGhr(w))
        val baseBypass  = update.valid && update.pht_index === baseIdx
        val baseCounter = Mux(baseBypass, baseNewCounter, basePht(baseIdx))
        val baseTaken   = baseCounter(SZ_BHT - 1)
        val hits        = Wire(Vec(tableCount, Bool()))
        val predictions = Wire(Vec(tableCount, Bool()))

        for (i <- 0 until tableCount) {
          val index = tableIndex(req.pc(w), queryGhr(w), i)
          val data  = tables(i)(w).read(index)
          hits(i)        := tableValid(i)(index) && entryTag(data, i) === tableTag(req.pc(w), queryGhr(w), i)
          predictions(i) := entryCounter(data)(counterWidth - 1)
        }

        var selected: Bool = baseTaken
        var alternate: Bool = baseTaken
        var provider: UInt = 0.U(providerWidth.W)

        for (i <- 0 until tableCount) {
          alternate = Mux(hits(i), selected, alternate)
          selected  = Mux(hits(i), predictions(i), selected)
          provider  = Mux(hits(i), (i + 1).U(providerWidth.W), provider)
        }

        resp.taken(w)        := selected
        resp.pht_index(w)    := baseIdx
        resp.ghr_snapshot(w) := queryGhr(w)
        resp.provider(w)     := provider
        resp.alt_taken(w)    := alternate
        queryGhr(w + 1)      := Mux(req.is_branch(w), shiftHist(queryGhr(w), selected), queryGhr(w))
      }

      val updateIndices = (0 until tableCount).map(i => tableIndex(update.pc, update.ghr_snapshot, i))
      val updateTags    = (0 until tableCount).map(i => tableTag(update.pc, update.ghr_snapshot, i))
      val updateData = (0 until tableCount).map(i => tables(i)(p(IssueWidth)).read(updateIndices(i)))
      val providerMatch = (0 until tableCount).map { i =>
        update.provider === (i + 1).U && tableValid(i)(updateIndices(i)) &&
          entryTag(updateData(i), i) === updateTags(i)
      }
      val directionMiss = update.valid && update.pred_taken =/= update.taken

      val allocate = Wire(Vec(tableCount, Bool()))
      var foundCandidate: Bool = false.B
      for (i <- 0 until tableCount) {
        val isLonger    = update.provider < (i + 1).U
        val replaceable = !tableValid(i)(updateIndices(i)) || entryUseful(updateData(i)) === 0.U
        val candidate   = isLonger && replaceable
        allocate(i)    := directionMiss && candidate && !foundCandidate
        foundCandidate  = foundCandidate || candidate
      }
      val allocationFailed = directionMiss && !foundCandidate

      for (i <- 0 until tableCount) {
        val oldTag     = entryTag(updateData(i), i)
        val oldCounter = entryCounter(updateData(i))
        val oldUseful  = entryUseful(updateData(i))
        val writeEnable = WireDefault(false.B)
        val writeData   = WireDefault(updateData(i))

        when(update.valid && providerMatch(i)) {
          val nextUseful = Mux(
            update.pred_taken =/= update.alt_taken,
            updateUseful(oldUseful, update.pred_taken === update.taken),
            oldUseful
          )
          writeEnable := true.B
          writeData := packEntry(oldTag, satUpdate(oldCounter, update.taken, counterWidth), nextUseful)
        }.elsewhen(allocate(i)) {
          val initialCounter = Mux(
            update.taken,
            (BigInt(1) << (counterWidth - 1)).U(counterWidth.W),
            ((BigInt(1) << (counterWidth - 1)) - 1).U(counterWidth.W)
          )
          writeEnable := true.B
          writeData   := packEntry(updateTags(i), initialCounter, 0.U(usefulWidth.W))
          tableValid(i) := tableValid(i) | UIntToOH(updateIndices(i), tableEntries(i))
        }.elsewhen(
          allocationFailed && update.provider < (i + 1).U && tableValid(i)(updateIndices(i)) &&
            oldUseful =/= 0.U
        ) {
          writeEnable := true.B
          writeData   := packEntry(oldTag, oldCounter, oldUseful - 1.U)
        }

        when(writeEnable) {
          for (r <- 0 until numReadPorts)
            tables(i)(r).write(updateIndices(i), writeData)
        }
      }

      when(update.valid) {
        basePht(update.pht_index) := baseNewCounter
        commitGhr                 := updateNextGhr
      }

      when(req.accept) {
        specGhr := queryGhr(p(IssueWidth))
      }

      when(req.flush) {
        specGhr := commitGhr
      }

      when(update.valid && update.mispredict) {
        specGhr := updateNextGhr
      }
    }
  }

  override def registry: NodeDimensionRegistry[PredictorKindImpl] =
    PredictorKindFactory
}
