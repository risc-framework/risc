package arch.core.scheduler.impls.policy.tomasulo

import arch.configs._
import arch.core.fupool.{ FuReq, FuResp, FunctionalUnitType }
import arch.core.sb.StoreAddressBundle
import arch.core.scheduler._
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.{ DecoupledIO, Mux1H, PopCount, PriorityEncoderOH, ValidIO, log2Ceil }

private class ReservationStationEntry(implicit p: Parameters) extends Bundle {
  val valid        = Bool()
  val sequence     = UInt(log2Ceil(2 * p(RobSize)).W)
  val op           = new FuReq
  val rs1Ready     = Bool()
  val rs2Ready     = Bool()
  val storeAddrKnown = Bool()
}

object TomasuloSchedulerPolicy extends RegisteredNodeUtils[SchedulerPolicyImpl] {
  override def utils: SchedulerPolicyImpl = new SchedulerPolicyImpl {
    override def value: String = "tomasulo"

    override def elaborate(
      flush: Bool,
      dispatched: Int => DecoupledIO[FuReq],
      fuReq: Int => DecoupledIO[FuReq],
      fuDone: Int => DecoupledIO[FuResp],
      storeAddr: Int => ValidIO[StoreAddressBundle],
      debug: SchedulerDebugInfo
    )(implicit p: Parameters): Unit = {
      val rsSize = p(ReservationStationSize)
      val seqW   = log2Ceil(2 * p(RobSize))

      require(rsSize >= p(IssueWidth), s"ReservationStationSize must be >= IssueWidth")

      def sequenceOlder(lhs: UInt, rhs: UInt): Bool = {
        val distance = rhs - lhs
        lhs =/= rhs && !distance(seqW - 1)
      }

      def cdbLookup(tag: UInt): (Bool, UInt) = {
        val hits = Wire(Vec(p(NumFUs), Bool()))

        for (f <- 0 until p(NumFUs))
          hits(f) := fuDone(f).fire && fuDone(f).bits.rob_tag === tag

        val hit = hits.asUInt.orR
        val data = Mux(
          hit,
          Mux1H(hits, (0 until p(NumFUs)).map(f => fuDone(f).bits.result)),
          0.U(p(XLen).W)
        )

        (hit, data)
      }

      def isMemory(op: FuReq): Bool =
        op.fu_type === FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD.index.U ||
          op.fu_type === FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST.index.U

      val entries         = RegInit(VecInit(Seq.fill(rsSize)(0.U.asTypeOf(new ReservationStationEntry))))
      val sequenceCounter = RegInit(0.U(seqW.W))
      val nextEntries     = WireDefault(entries)
      val rs1Wake         = Wire(Vec(rsSize, Bool()))
      val rs2Wake         = Wire(Vec(rsSize, Bool()))
      val issueEntries    = Wire(Vec(rsSize, new FuReq))
      val storeAddrValidReg = RegInit(false.B)
      val storeAddrBitsReg  = Reg(new StoreAddressBundle)

      for (f <- 0 until p(NumFUs)) {
        fuReq(f).valid  := false.B
        fuReq(f).bits   := 0.U.asTypeOf(new FuReq)
        fuDone(f).ready := true.B
      }

      for (i <- 0 until rsSize) {
        val (rs1Hit, rs1Data) = cdbLookup(entries(i).op.rs1_tag)
        val (rs2Hit, rs2Data) = cdbLookup(entries(i).op.rs2_tag)

        rs1Wake(i)     := entries(i).valid && !entries(i).rs1Ready && rs1Hit
        rs2Wake(i)     := entries(i).valid && !entries(i).rs2Ready && rs2Hit

        when(rs1Wake(i)) {
          nextEntries(i).rs1Ready    := true.B
          // Loads and stores already wait for this registered wakeup before
          // issue.  Fold their effective address into the same RS write so
          // the later age-select -> FU accept path does not contain a 32-bit
          // address carry chain.
          nextEntries(i).op.rs1_data := Mux(
            isMemory(entries(i).op),
            rs1Data + entries(i).op.imm,
            rs1Data
          )
          nextEntries(i).op.imm := Mux(
            isMemory(entries(i).op),
            0.U,
            entries(i).op.imm
          )
        }

        when(rs2Wake(i)) {
          nextEntries(i).rs2Ready   := true.B
          nextEntries(i).op.rs2_data := rs2Data
        }

        issueEntries(i)             := entries(i).op
        issueEntries(i).rs1_data    := Mux(rs1Wake(i), rs1Data, entries(i).op.rs1_data)
        issueEntries(i).rs2_data    := Mux(rs2Wake(i), rs2Data, entries(i).op.rs2_data)
      }

      val issuedMask       = Wire(Vec(p(NumFUs), UInt(rsSize.W)))
      val older            = Wire(Vec(rsSize, Vec(rsSize, Bool())))
      val registeredReady  = Wire(Vec(rsSize, Bool()))
      val operandsReady    = Wire(Vec(rsSize, Bool()))
      val serializingReady = Wire(Vec(rsSize, Bool()))

      for (i <- 0 until rsSize) {
        older(i)(i) := false.B

        for (j <- i + 1 until rsSize) {
          // Valid RS entries have unique sequence numbers, making pair order complementary.
          val iOlder = sequenceOlder(entries(i).sequence, entries(j).sequence)

          older(i)(j) := iOlder
          older(j)(i) := !iOlder
        }
      }

      // Capture addresses for stores whose base becomes ready after dispatch,
      // without waiting for their data operand. The registered sideband keeps
      // RS age selection and address generation out of the StoreBuffer path.
      val storeAddrCandidates = VecInit((0 until rsSize).map { i =>
        entries(i).valid &&
          entries(i).op.fu_type === FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST.index.U &&
          entries(i).rs1Ready && !entries(i).storeAddrKnown
      })
      // One oldest publication per cycle is sufficient: StoreBuffer ordering
      // remains authoritative, while avoiding two 64-entry rank/popcount trees.
      val oldestStoreAddrSelect = VecInit((0 until rsSize).map { i =>
        val olderCandidate = (0 until rsSize)
          .filter(_ != i)
          .map(j => storeAddrCandidates(j) && older(j)(i))
          .reduce(_ || _)

        storeAddrCandidates(i) && !olderCandidate
      }).asUInt
      val storeAddrSelect = oldestStoreAddrSelect
      val publishStoreAddr = storeAddrCandidates.asUInt.orR
      val selectedStoreOp  = Wire(new FuReq)

      selectedStoreOp := Mux1H(storeAddrSelect, entries.map(_.op))

      for (s <- 0 until p(NumSTs)) {
        if (s == 0) {
          storeAddr(s).valid := storeAddrValidReg && !flush
          storeAddr(s).bits  := storeAddrBitsReg
        } else {
          storeAddr(s).valid := false.B
          storeAddr(s).bits  := 0.U.asTypeOf(new StoreAddressBundle)
        }
      }

      when(flush) {
        storeAddrValidReg := false.B
      }.otherwise {
        storeAddrValidReg := publishStoreAddr
        when(publishStoreAddr) {
          storeAddrBitsReg.sq_idx := selectedStoreOp.sq_idx
          storeAddrBitsReg.addr   := selectedStoreOp.rs1_data
        }
      }

      val storeAddrPublished = storeAddrSelect
      for (i <- 0 until rsSize)
        when(storeAddrPublished(i)) {
          nextEntries(i).storeAddrKnown := true.B
        }

      for (i <- 0 until rsSize) {
        val entry = entries(i)
        val olderEntries = (0 until rsSize)
          .filter(_ != i)
          .map(j => entries(j).valid && older(j)(i))
        val olderEntry = olderEntries.reduce(_ || _)
        val olderSerializing = (0 until rsSize)
          .filter(_ != i)
          .map(j => entries(j).valid && older(j)(i) &&
            entries(j).op.fu_type === FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR.index.U)
          .reduce(_ || _)

        registeredReady(i) := entry.rs1Ready && entry.rs2Ready
        operandsReady(i) := (entry.rs1Ready || rs1Wake(i)) &&
          (entry.rs2Ready || rs2Wake(i))
        serializingReady(i) := Mux(
          entry.op.fu_type === FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR.index.U,
          !olderEntry,
          !olderSerializing
        )
      }

      // All LDs consume the same precomputed age-ranked candidates.  A late
      // completion -> ready signal then controls only a small final rank mux,
      // rather than replicating the RS-wide age-rank/select tree per Load FU.
      val loadFuIndices = (0 until p(NumFUs)).filter { f =>
        p(FunctionalUnits)(f).`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD
      }
      val loadCandidates = Wire(Vec(rsSize, Bool()))
      val olderLoadCount = Wire(Vec(rsSize, UInt(log2Ceil(rsSize + 1).W)))

      for (i <- 0 until rsSize) {
        val entry = entries(i)
        loadCandidates(i) := entry.valid && registeredReady(i) && serializingReady(i) &&
          entry.op.fu_type === FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD.index.U
        olderLoadCount(i) := PopCount(VecInit(
          (0 until rsSize)
            .filter(_ != i)
            .map(j => loadCandidates(j) && older(j)(i))
        ))
      }

      val oldestLoadOH = VecInit((0 until rsSize).map { i =>
        loadCandidates(i) && olderLoadCount(i) === 0.U
      }).asUInt
      val secondLoadOH = VecInit((0 until rsSize).map { i =>
        loadCandidates(i) && olderLoadCount(i) === 1.U
      }).asUInt
      val thirdLoadOH = VecInit((0 until rsSize).map { i =>
        loadCandidates(i) && olderLoadCount(i) === 2.U
      }).asUInt
      val oldestLoadOp = Mux1H(oldestLoadOH, entries.map(_.op))
      val secondLoadOp = Mux1H(secondLoadOH, entries.map(_.op))
      val thirdLoadOp  = Mux1H(thirdLoadOH, entries.map(_.op))

      for (f <- 0 until p(NumFUs)) {
        val isLoadUnit =
          p(FunctionalUnits)(f).`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD

        if (isLoadUnit && (loadFuIndices.size == 2 || loadFuIndices.size == 3)) {
          val loadPosition = loadFuIndices.indexOf(f)
          val priorLoadFires = loadFuIndices
            .take(loadPosition)
            .map(prev => fuReq(prev).fire)
          val acceptedRank = if (priorLoadFires.isEmpty)
            0.U(log2Ceil(loadFuIndices.size + 1).W)
          else
            PopCount(VecInit(priorLoadFires))
          val selectOH = if (loadFuIndices.size == 2)
            Mux(acceptedRank === 1.U, secondLoadOH, oldestLoadOH)
          else
            Mux(
              acceptedRank === 2.U,
              thirdLoadOH,
              Mux(acceptedRank === 1.U, secondLoadOH, oldestLoadOH)
            )
          val selected = PopCount(loadCandidates) > acceptedRank
          val issueOp        = Wire(new FuReq)

          if (loadFuIndices.size == 2)
            issueOp := Mux(acceptedRank === 1.U, secondLoadOp, oldestLoadOp)
          else
            issueOp := Mux(
              acceptedRank === 2.U,
              thirdLoadOp,
              Mux(acceptedRank === 1.U, secondLoadOp, oldestLoadOp)
            )

          // Ready memory entries carry their effective address in rs1_data.
          // Keep this explicit constant at the FU boundary so synthesis can
          // remove the redundant address adder rather than relying on the RS
          // value invariant alone.
          issueOp.imm   := 0.U
          issueOp.fu_id := f.U

          fuReq(f).valid := selected
          fuReq(f).bits  := issueOp
          issuedMask(f)  := Mux(fuReq(f).fire, selectOH, 0.U)
        } else {
          val baseCandidates = Wire(Vec(rsSize, Bool()))
          val isStoreUnit =
            p(FunctionalUnits)(f).`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST
          val isMemoryUnit = isLoadUnit || isStoreUnit
          val useRegisteredOperands =
            isMemoryUnit ||
              p(FunctionalUnits)(f).`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_DIV
          val priorSameTypeAccepted = (0 until f)
            .filter(prev => p(FunctionalUnits)(prev).`type` == p(FunctionalUnits)(f).`type`)
            .map(prev => fuReq(prev).fire)

          for (i <- 0 until rsSize) {
            val entry = entries(i)
            // Memory operations and divides issue only from operands captured in
            // the RS on a previous cycle, cutting their same-cycle CDB paths.
            val issueOperandsReady =
              if (useRegisteredOperands)
                registeredReady(i)
              else
                operandsReady(i)

            baseCandidates(i) := entry.valid && issueOperandsReady && serializingReady(i) &&
              entry.op.fu_type === p(FunctionalUnits)(f).`type`.index.U
          }

          val selectOH = if (priorSameTypeAccepted.nonEmpty) {
            val acceptedRank = PopCount(VecInit(priorSameTypeAccepted))
            VecInit((0 until rsSize).map { i =>
              val olderCandidateCount = PopCount(VecInit(
                (0 until rsSize)
                  .filter(_ != i)
                  .map(j => baseCandidates(j) && older(j)(i))
              ))
              baseCandidates(i) && olderCandidateCount === acceptedRank
            }).asUInt
          } else {
            val oldest = Wire(Vec(rsSize, Bool()))

            for (i <- 0 until rsSize) {
              val olderCandidate = (0 until rsSize)
                .filter(_ != i)
                .map(j => baseCandidates(j) && older(j)(i))
                .reduce(_ || _)

              oldest(i) := baseCandidates(i) && !olderCandidate
            }

            oldest.asUInt
          }

          val selected = if (priorSameTypeAccepted.nonEmpty)
            PopCount(baseCandidates) > PopCount(VecInit(priorSameTypeAccepted))
          else
            baseCandidates.asUInt.orR
          val issueOp = Wire(new FuReq)

          if (useRegisteredOperands)
            issueOp := Mux1H(selectOH, entries.map(_.op))
          else
            issueOp := Mux1H(selectOH, issueEntries)

          if (isMemoryUnit)
            issueOp.imm := 0.U

          issueOp.fu_id := f.U

          // Keep Decoupled valid independent of downstream ready. The actual
          // issue and RS removal remain guarded by fire below.
          fuReq(f).valid := selected
          fuReq(f).bits  := issueOp
          issuedMask(f)  := Mux(fuReq(f).fire, selectOH, 0.U)
        }
      }

      val allIssued = issuedMask.reduce(_ | _)

      for (i <- 0 until rsSize)
        when(allIssued(i)) {
          nextEntries(i).valid := false.B
        }

      val freeEntries = Wire(Vec(p(IssueWidth) + 1, UInt(rsSize.W)))
      val consumed    = Wire(Vec(p(IssueWidth), Bool()))
      val allocOH     = Wire(Vec(p(IssueWidth), UInt(rsSize.W)))

      // Keep dispatch capacity on registered RS occupancy.  An entry issued in
      // this cycle becomes visible as free after the clock edge, cutting the
      // completion/wakeup/issue path out of dispatch backpressure.
      freeEntries(0) := VecInit(entries.map(e => !e.valid)).asUInt

      for (w <- 0 until p(IssueWidth)) {
        val (rs1Hit, rs1Data) = cdbLookup(dispatched(w).bits.rs1_tag)
        val (rs2Hit, rs2Data) = cdbLookup(dispatched(w).bits.rs2_tag)
        val rs1Wake            = dispatched(w).bits.rs1_pending && rs1Hit
        val rs2Wake            = dispatched(w).bits.rs2_pending && rs2Hit
        val previousConsumed = (0 until w)
          .map(i => !dispatched(i).valid || consumed(i))
          .reduceOption(_ && _)
          .getOrElse(true.B)

        allocOH(w) := PriorityEncoderOH(freeEntries(w))
        dispatched(w).ready := !flush && previousConsumed && freeEntries(w).orR
        consumed(w) := dispatched(w).fire
        freeEntries(w + 1) := freeEntries(w) & ~Mux(consumed(w), allocOH(w), 0.U)

        for (i <- 0 until rsSize)
          when(consumed(w) && allocOH(w)(i)) {
            val dispatchRs1 = Mux(rs1Wake, rs1Data, dispatched(w).bits.rs1_data)
            val rs1Ready = !dispatched(w).bits.rs1_pending || rs1Wake
            val memory = isMemory(dispatched(w).bits)

            nextEntries(i)             := 0.U.asTypeOf(new ReservationStationEntry)
            nextEntries(i).valid       := true.B
            nextEntries(i).sequence    := sequenceCounter +
              (if (w == 0) 0.U else PopCount(consumed.take(w)))
            nextEntries(i).op          := dispatched(w).bits
            nextEntries(i).rs1Ready    := rs1Ready
            nextEntries(i).rs2Ready    := !dispatched(w).bits.rs2_pending || rs2Wake
            nextEntries(i).storeAddrKnown :=
              dispatched(w).bits.fu_type =/=
                FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST.index.U ||
                !dispatched(w).bits.rs1_pending
            nextEntries(i).op.rs1_data := Mux(
              memory && rs1Ready,
              dispatchRs1 + dispatched(w).bits.imm,
              dispatchRs1
            )
            nextEntries(i).op.imm := Mux(
              memory && rs1Ready,
              0.U,
              dispatched(w).bits.imm
            )
            nextEntries(i).op.rs2_data := Mux(rs2Wake, rs2Data, dispatched(w).bits.rs2_data)
          }
      }

      when(flush) {
        entries         := 0.U.asTypeOf(entries)
        sequenceCounter := 0.U
      }.otherwise {
        entries         := nextEntries
        sequenceCounter := sequenceCounter + PopCount(consumed)
      }

      val waitingOperands = entries.map(e => e.valid && (!e.rs1Ready || !e.rs2Ready)).reduce(_ || _)
      val stationFull     = dispatched(0).valid && !freeEntries(0).orR

      debug.raw_wait         := stationFull && waitingOperands
      debug.waw_wait         := false.B
      debug.fu_busy          := stationFull && !waitingOperands
      debug.older_lane_block :=
        (1 until p(IssueWidth))
          .map(w => dispatched(w).valid && !consumed(w - 1))
          .reduceOption(_ || _)
          .getOrElse(false.B)
      debug.no_matching_fu := false.B
    }
  }

  override def registry: NodeDimensionRegistry[SchedulerPolicyImpl] =
    SchedulerPolicyFactory
}
