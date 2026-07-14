package arch.core.scheduler.impls.policy.tomasulo

import arch.configs._
import arch.core.fupool.{ FuReq, FuResp, FunctionalUnitType }
import arch.core.scheduler._
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }
import chisel3._
import chisel3.util.{ Cat, DecoupledIO, Fill, Mux1H, PopCount, PriorityEncoderOH, log2Ceil }

private class ReservationStationEntry(implicit p: Parameters) extends Bundle {
  val valid        = Bool()
  val sequence     = UInt(log2Ceil(2 * p(RobSize)).W)
  val op           = new FuReq
  val rs1Ready     = Bool()
  val rs2Ready     = Bool()
  val addrPrepared = Bool()
}

object TomasuloSchedulerPolicy extends RegisteredNodeUtils[SchedulerPolicyImpl] {
  override def utils: SchedulerPolicyImpl = new SchedulerPolicyImpl {
    override def value: String = "tomasulo"

    override def elaborate(
      flush: Bool,
      dispatched: Int => DecoupledIO[FuReq],
      fuReq: Int => DecoupledIO[FuReq],
      fuDone: Int => DecoupledIO[FuResp],
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

      def isMemory(op: FuReq): Bool = {
        val opcode = op.instr(6, 0)
        opcode === "b0000011".U || opcode === "b0100011".U
      }

      def memoryImmediate(op: FuReq): UInt = {
        val loadImm  = Cat(Fill(p(XLen) - 12, op.instr(31)), op.instr(31, 20))
        val storeImm = Cat(Fill(p(XLen) - 12, op.instr(31)), op.instr(31, 25), op.instr(11, 7))
        Mux(op.instr(6, 0) === "b0100011".U, storeImm, loadImm)
      }

      val entries         = RegInit(VecInit(Seq.fill(rsSize)(0.U.asTypeOf(new ReservationStationEntry))))
      val sequenceCounter = RegInit(0.U(seqW.W))
      val nextEntries     = WireDefault(entries)
      val rs1Wake         = Wire(Vec(rsSize, Bool()))
      val rs2Wake         = Wire(Vec(rsSize, Bool()))
      val issueEntries    = Wire(Vec(rsSize, new FuReq))

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
          nextEntries(i).rs1Ready := true.B
          // Memory operations issue only after registered wakeup, so fold the
          // effective address while capturing the CDB value.
          val memory = isMemory(entries(i).op)
          nextEntries(i).op.rs1_data := Mux(
            memory,
            rs1Data + memoryImmediate(entries(i).op),
            rs1Data
          )
          nextEntries(i).op.imm      := Mux(memory, 0.U, entries(i).op.imm)
          nextEntries(i).addrPrepared := true.B
        }

        when(rs2Wake(i)) {
          nextEntries(i).rs2Ready   := true.B
          nextEntries(i).op.rs2_data := rs2Data
        }

        // Ready memory operations spend one local RS cycle preparing their
        // address. This removes the adder from both dispatch and issue paths.
        when(entries(i).valid && entries(i).rs1Ready && !entries(i).addrPrepared) {
          nextEntries(i).op.rs1_data := entries(i).op.rs1_data + memoryImmediate(entries(i).op)
          nextEntries(i).op.imm      := 0.U
          nextEntries(i).addrPrepared := true.B
        }

        issueEntries(i)             := entries(i).op
        issueEntries(i).rs1_data    := Mux(rs1Wake(i), rs1Data, entries(i).op.rs1_data)
        issueEntries(i).rs2_data    := Mux(rs2Wake(i), rs2Data, entries(i).op.rs2_data)
      }

      val issuedMask       = Wire(Vec(p(NumFUs), UInt(rsSize.W)))
      val older            = Wire(Vec(rsSize, Vec(rsSize, Bool())))
      val registeredReady  = Wire(Vec(rsSize, Bool()))
      val operandsReady    = Wire(Vec(rsSize, Bool()))
      val loadReady        = Wire(Vec(rsSize, Bool()))
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

      for (i <- 0 until rsSize) {
        val entry = entries(i)
        val olderEntries = (0 until rsSize)
          .filter(_ != i)
          .map(j => entries(j).valid && older(j)(i))
        val olderEntry = olderEntries.reduce(_ || _)
        val olderStore = (0 until rsSize)
          .filter(_ != i)
          .map(j => entries(j).valid && older(j)(i) &&
            entries(j).op.fu_type === FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST.index.U)
          .reduce(_ || _)
        val olderSerializing = (0 until rsSize)
          .filter(_ != i)
          .map(j => entries(j).valid && older(j)(i) &&
            entries(j).op.fu_type === FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR.index.U)
          .reduce(_ || _)

        registeredReady(i) := entry.rs1Ready && entry.rs2Ready
        operandsReady(i) := (entry.rs1Ready || rs1Wake(i)) &&
          (entry.rs2Ready || rs2Wake(i))
        loadReady(i) := entry.op.fu_type =/= FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD.index.U ||
          !olderStore
        serializingReady(i) := Mux(
          entry.op.fu_type === FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR.index.U,
          !olderEntry,
          !olderSerializing
        )
      }

      for (f <- 0 until p(NumFUs)) {
        val baseCandidates = Wire(Vec(rsSize, Bool()))
        val isLoadUnit =
          p(FunctionalUnits)(f).`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD
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
          // Loads and divides issue only from operands captured in the RS on a
          // previous cycle, cutting their same-cycle CDB paths.
          val issueOperandsReady =
            if (useRegisteredOperands)
              registeredReady(i) && (if (isMemoryUnit) entry.addrPrepared else true.B)
            else
              operandsReady(i)

          baseCandidates(i) := entry.valid && issueOperandsReady && loadReady(i) &&
            serializingReady(i) &&
            entry.op.fu_type === p(FunctionalUnits)(f).`type`.index.U
        }

        val selectOH = if (priorSameTypeAccepted.nonEmpty) {
          // Select the candidate whose age rank equals the number of earlier
          // same-type FUs that accepted a request. This is equivalent to
          // repeatedly masking each earlier oldest entry, but computes later
          // FU data selection in parallel with the earlier one-hot selectors.
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

        // A later same-type FU only needs to know whether enough candidates
        // remain. Keep its valid path independent of the earlier FU's
        // one-hot selection.
        val selected = if (priorSameTypeAccepted.nonEmpty)
          PopCount(baseCandidates) > PopCount(VecInit(priorSameTypeAccepted))
        else
          baseCandidates.asUInt.orR
        val issueOp  = Wire(new FuReq)

        if (useRegisteredOperands)
          issueOp := Mux1H(selectOH, entries.map(_.op))
        else
          issueOp := Mux1H(selectOH, issueEntries)

        if (isMemoryUnit)
          issueOp.imm := 0.U

        issueOp.fu_id := f.U

        fuReq(f).valid := selected && fuReq(f).ready
        fuReq(f).bits  := issueOp
        issuedMask(f)  := Mux(fuReq(f).fire, selectOH, 0.U)
      }

      val allIssued = issuedMask.reduce(_ | _)

      for (i <- 0 until rsSize)
        when(allIssued(i)) {
          nextEntries(i).valid := false.B
        }

      val freeEntries = Wire(Vec(p(IssueWidth) + 1, UInt(rsSize.W)))
      val consumed    = Wire(Vec(p(IssueWidth), Bool()))
      val allocOH     = Wire(Vec(p(IssueWidth), UInt(rsSize.W)))

      // Keep dispatch backpressure independent of same-cycle wakeup and issue decisions.
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
            val memory = isMemory(dispatched(w).bits)
            val rs1Ready = !dispatched(w).bits.rs1_pending || rs1Wake

            nextEntries(i)             := 0.U.asTypeOf(new ReservationStationEntry)
            nextEntries(i).valid       := true.B
            nextEntries(i).sequence    := sequenceCounter +
              (if (w == 0) 0.U else PopCount(consumed.take(w)))
            nextEntries(i).op          := dispatched(w).bits
            nextEntries(i).rs1Ready    := rs1Ready
            nextEntries(i).rs2Ready    := !dispatched(w).bits.rs2_pending || rs2Wake
            nextEntries(i).addrPrepared := !memory
            nextEntries(i).op.rs1_data := dispatchRs1
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
