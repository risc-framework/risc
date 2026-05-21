package arch.core.bpu

import arch.configs._
import chisel3._
import chisel3.util.{ PriorityEncoder, UIntToOH, log2Ceil }

class BtbEntry(tagWidth: Int)(implicit p: Parameters) extends Bundle with BHTConsts {
  val valid  = Bool()
  val tag    = UInt(tagWidth.W)
  val target = UInt(p(XLen).W)
  val ctrl   = UInt(SZ_BHT.W)
}

object BtbEntry extends BHTConsts {
  def default(tagWidth: Int)(implicit p: Parameters): BtbEntry = {
    val e = Wire(new BtbEntry(tagWidth))
    e.valid  := false.B
    e.tag    := 0.U
    e.target := 0.U
    e.ctrl   := BHT_WT.value.U(SZ_BHT.W)
    e
  }
}

class BpuUpdate(implicit p: Parameters) extends Bundle {
  val valid        = Bool()
  val pc           = UInt(p(XLen).W)
  val target       = UInt(p(XLen).W)
  val taken        = Bool()
  val pht_index    = UInt(p(GShareGhrWidth).W)
  val ghr_snapshot = UInt(p(GShareGhrWidth).W)
  val mispredict   = Bool()
}

class Btb(implicit p: Parameters) extends Module with BHTConsts {
  override def desiredName: String = s"${p(ISA).name}_btb"

  private val rawIndexWidth = log2Ceil(p(BTBSets))
  private val indexWidth    = rawIndexWidth.max(1)
  private val tagWidth      = p(XLen) - rawIndexWidth - p(PCAlign)
  private val wayWidth      = log2Ceil(p(BTBWays)).max(1)
  private val numReadPorts  = p(IssueWidth) + 1

  val query_pc  = IO(Input(Vec(p(IssueWidth), UInt(p(XLen).W))))
  val hit       = IO(Output(Vec(p(IssueWidth), Bool())))
  val entry_out = IO(Output(Vec(p(IssueWidth), new BtbEntry(tagWidth))))
  val update    = IO(Input(new BpuUpdate))

  val validBits = RegInit(VecInit(Seq.fill(p(BTBSets))(0.U(p(BTBWays).W))))

  val tagArray    = Seq.fill(numReadPorts)(Mem(p(BTBSets), Vec(p(BTBWays), UInt(tagWidth.W))))
  val targetArray = Seq.fill(numReadPorts)(Mem(p(BTBSets), Vec(p(BTBWays), UInt(p(XLen).W))))
  val ctrlArray   = Seq.fill(numReadPorts)(Mem(p(BTBSets), Vec(p(BTBWays), UInt(SZ_BHT.W))))

  val replStates = Seq.fill(p(BTBSets))(p(BTBReplPolicy).build(p(BTBWays)))

  def getIndex(pc: UInt): UInt =
    if (rawIndexWidth > 0) pc(rawIndexWidth + p(PCAlign) - 1, p(PCAlign)) else 0.U(indexWidth.W)

  def getTag(pc: UInt): UInt =
    pc(p(XLen) - 1, rawIndexWidth + p(PCAlign))

  val victimWayReg = RegInit(VecInit(Seq.fill(p(BTBSets))(0.U(wayWidth.W))))
  for (s <- 0 until p(BTBSets)) victimWayReg(s) := replStates(s).getVictim()

  for (q <- 0 until p(IssueWidth)) {
    val qIndex   = getIndex(query_pc(q))
    val qTag     = getTag(query_pc(q))
    val qValid   = validBits(qIndex)
    val qTags    = tagArray(q).read(qIndex)
    val qTargets = targetArray(q).read(qIndex)
    val qCtrls   = ctrlArray(q).read(qIndex)
    val hitBits  = Wire(Vec(p(BTBWays), Bool()))

    for (w <- 0 until p(BTBWays))
      hitBits(w) := qValid(w) && qTags(w) === qTag

    val anyHit = hitBits.asUInt.orR
    val hitWay = PriorityEncoder(hitBits)

    hit(q)              := anyHit
    entry_out(q).valid  := anyHit
    entry_out(q).tag    := Mux(anyHit, qTags(hitWay), 0.U)
    entry_out(q).target := Mux(anyHit, qTargets(hitWay), 0.U)
    entry_out(q).ctrl   := Mux(anyHit, qCtrls(hitWay), BHT_WT.value.U(SZ_BHT.W))
  }

  when(update.valid && update.taken) {
    val uIndex   = getIndex(update.pc)
    val uTag     = getTag(update.pc)
    val uValid   = validBits(uIndex)
    val uTags    = tagArray(p(IssueWidth)).read(uIndex)
    val uTargets = targetArray(p(IssueWidth)).read(uIndex)
    val uCtrls   = ctrlArray(p(IssueWidth)).read(uIndex)
    val uHitBits = Wire(Vec(p(BTBWays), Bool()))

    for (w <- 0 until p(BTBWays))
      uHitBits(w) := uValid(w) && uTags(w) === uTag

    val uAnyHit   = uHitBits.asUInt.orR
    val uHitWay   = PriorityEncoder(uHitBits)
    val victimWay = victimWayReg(uIndex)
    val writeWay  = Mux(uAnyHit, uHitWay, victimWay)
    val oldCtrl   = Mux(uAnyHit, uCtrls(writeWay), BHT_WNT.value.U(SZ_BHT.W))
    val nextCtrl  = Mux(oldCtrl === BHT_ST.value.U, BHT_ST.value.U, oldCtrl + 1.U)

    val nextValid   = uValid | UIntToOH(writeWay, p(BTBWays))
    val nextTags    = Wire(Vec(p(BTBWays), UInt(tagWidth.W)))
    val nextTargets = Wire(Vec(p(BTBWays), UInt(p(XLen).W)))
    val nextCtrls   = Wire(Vec(p(BTBWays), UInt(SZ_BHT.W)))

    for (w <- 0 until p(BTBWays)) {
      nextTags(w)    := Mux(writeWay === w.U, uTag, uTags(w))
      nextTargets(w) := Mux(writeWay === w.U, update.target, uTargets(w))
      nextCtrls(w)   := Mux(writeWay === w.U, nextCtrl, uCtrls(w))
    }

    validBits(uIndex) := nextValid

    for (r <- 0 until numReadPorts) {
      tagArray(r).write(uIndex, nextTags)
      targetArray(r).write(uIndex, nextTargets)
      ctrlArray(r).write(uIndex, nextCtrls)
    }

    for (s <- 0 until p(BTBSets))
      when(s.U === uIndex) {
        replStates(s).update(writeWay, uAnyHit)
      }
  }
}
