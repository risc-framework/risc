package arch.core.bpu.impls.predictor

import arch.core.bpu._
import arch.configs._
import vutils.graph.{ NodeRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.Cat

object GSharePredictor extends RegisteredNodeUtils[PredictorKindImpl] with BHTConsts {
  override def utils: PredictorKindImpl = new PredictorKindImpl with BHTConsts {
    override def value: String = "gshare"

    override def elaborate(io: PredictorIO)(implicit p: Parameters): Unit = {
      val phtEntries = 1 << p(GShareGhrWidth)

      require(p(GShareGhrWidth) >= 2, "GShareGhrWidth must be at least 2")
      require(SZ_BHT >= 2, "BHT counter width must be at least 2")

      val commitGhr = RegInit(0.U(p(GShareGhrWidth).W))
      val specGhr   = RegInit(0.U(p(GShareGhrWidth).W))
      val pht       = RegInit(VecInit(Seq.fill(phtEntries)(BHT_WT.value.U(SZ_BHT.W))))

      def foldPc(pc: UInt): UInt = {
        val chunks = (p(PCAlign) until p(XLen) by p(GShareGhrWidth)).map { lo =>
          val hi   = (lo + p(GShareGhrWidth) - 1).min(p(XLen) - 1)
          val w    = hi - lo + 1
          val bits = pc(hi, lo)

          if (w == p(GShareGhrWidth)) bits else Cat(0.U((p(GShareGhrWidth) - w).W), bits)
        }

        chunks.reduce(_ ^ _)
      }

      def getIndex(pc: UInt, hist: UInt): UInt =
        foldPc(pc) ^ hist

      def shiftHist(hist: UInt, isTaken: Bool): UInt =
        Cat(hist(p(GShareGhrWidth) - 2, 0), isTaken)

      def satUpdate(oldCnt: UInt, isTaken: Bool): UInt =
        Mux(
          isTaken,
          Mux(oldCnt === BHT_ST.value.U, BHT_ST.value.U, oldCnt + 1.U),
          Mux(oldCnt === BHT_SNT.value.U, BHT_SNT.value.U, oldCnt - 1.U)
        )

      def predictTaken(counter: UInt): Bool =
        counter(SZ_BHT - 1)

      val updateOldCnt  = pht(io.update.update.pht_index)
      val updateNewCnt  = satUpdate(updateOldCnt, io.update.update.taken)
      val updateNextGhr = shiftHist(io.update.update.ghr_snapshot, io.update.update.taken)

      val queryGhr = Wire(Vec(p(IssueWidth) + 1, UInt(p(GShareGhrWidth).W)))
      queryGhr(0) := specGhr

      for (w <- 0 until p(IssueWidth)) {
        val index      = getIndex(io.query.pc(w), queryGhr(w))
        val rawCounter = pht(index)
        val bypassHit  = io.update.update.valid && io.update.update.pht_index === index
        val counter    = Mux(bypassHit, updateNewCnt, rawCounter)
        val dirTaken   = predictTaken(counter)

        io.query.taken(w)        := dirTaken
        io.query.pht_index(w)    := index
        io.query.ghr_snapshot(w) := queryGhr(w)
        queryGhr(w + 1)          := Mux(io.query.is_branch(w), shiftHist(queryGhr(w), dirTaken), queryGhr(w))
      }

      when(io.update.update.valid) {
        pht(io.update.update.pht_index) := updateNewCnt
        commitGhr                       := updateNextGhr
      }

      when(io.query.accept) {
        specGhr := queryGhr(p(IssueWidth))
      }

      when(io.query.flush) {
        specGhr := commitGhr
      }

      when(io.update.update.valid && io.update.update.mispredict) {
        specGhr := updateNextGhr
      }
    }
  }

  override def registry: NodeRegistry[PredictorKindImpl] = PredictorKindFactory
}
