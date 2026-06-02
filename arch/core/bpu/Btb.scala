package arch.core.bpu

import arch.configs._
import vutils.graph.{ Node, NodeType }
import chisel3._
import chisel3.util.{ PriorityEncoder, UIntToOH, log2Ceil }

class BtbIO(implicit p: Parameters) extends Bundle {
  val query  = new BtbQueryIO
  val update = new BpuUpdateIO
}

class Btb(implicit p: Parameters) extends Node(new BtbIO) with BHTConsts {
  override def nodeType: NodeType  = BtbMeta.Type
  override def desiredName: String = "btb"

  private val rawIndexWidth = log2Ceil(p(BTBSets))
  private val indexWidth    = rawIndexWidth.max(1)
  private val tagWidth      = p(XLen) - rawIndexWidth - p(PCAlign)
  private val wayWidth      = log2Ceil(p(BTBWays)).max(1)
  private val numReadPorts  = p(IssueWidth) + 1

  private val validBits   = RegInit(VecInit(Seq.fill(p(BTBSets))(0.U(p(BTBWays).W))))
  private val tagArray    = Seq.fill(numReadPorts)(Mem(p(BTBSets), Vec(p(BTBWays), UInt(tagWidth.W))))
  private val targetArray =
    Seq.fill(numReadPorts)(Mem(p(BTBSets), Vec(p(BTBWays), UInt(p(XLen).W))))
  private val ctrlArray   = Seq.fill(numReadPorts)(Mem(p(BTBSets), Vec(p(BTBWays), UInt(SZ_BHT.W))))
  private val replStates  = Seq.fill(p(BTBSets))(p(BTBReplPolicy).build(p(BTBWays)))

  private def getIndex(pc: UInt): UInt =
    if (rawIndexWidth > 0) pc(rawIndexWidth + p(PCAlign) - 1, p(PCAlign)) else 0.U(indexWidth.W)

  private def getTag(pc: UInt): UInt =
    pc(p(XLen) - 1, rawIndexWidth + p(PCAlign))

  private val victimWayReg = RegInit(VecInit(Seq.fill(p(BTBSets))(0.U(wayWidth.W))))

  for (s <- 0 until p(BTBSets))
    victimWayReg(s) := replStates(s).getVictim()

  for (q <- 0 until p(IssueWidth)) {
    val qIndex   = getIndex(io.query.pc(q))
    val qTag     = getTag(io.query.pc(q))
    val qValid   = validBits(qIndex)
    val qTags    = tagArray(q).read(qIndex)
    val qTargets = targetArray(q).read(qIndex)
    val qCtrls   = ctrlArray(q).read(qIndex)
    val hitBits  = Wire(Vec(p(BTBWays), Bool()))

    for (w <- 0 until p(BTBWays))
      hitBits(w) := qValid(w) && qTags(w) === qTag

    val anyHit = hitBits.asUInt.orR
    val hitWay = PriorityEncoder(hitBits)

    io.query.hit(q)              := anyHit
    io.query.entry_out(q).valid  := anyHit
    io.query.entry_out(q).tag    := Mux(anyHit, qTags(hitWay), 0.U)
    io.query.entry_out(q).target := Mux(anyHit, qTargets(hitWay), 0.U)
    io.query.entry_out(q).ctrl   := Mux(anyHit, qCtrls(hitWay), BHT_WT.value.U(SZ_BHT.W))
  }

  when(io.update.update.valid && io.update.update.taken) {
    val uIndex   = getIndex(io.update.update.pc)
    val uTag     = getTag(io.update.update.pc)
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
      nextTargets(w) := Mux(writeWay === w.U, io.update.update.target, uTargets(w))
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
