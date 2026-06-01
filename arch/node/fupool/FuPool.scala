package arch.node.fupool

import arch.node.alu.Alu
import arch.node.bru.{ Bru, BruResolveIO }
import arch.node.div.Div
import arch.node.ld.{ Ld, LdMemIO, LdSbFwdIO }
import arch.node.mult.Mult
import arch.node.st.{ St, StSbWriteIO }
import arch.core.fu.FunctionalUnitType
import arch.configs._
import vutils.graph.{ Node, NodeType }
import chisel3._

class VecLdMemIO(implicit p: Parameters) extends Bundle {
  private val n = p(FunctionalUnits).count(_.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD)
  val ports     = Vec(n, new LdMemIO)
}

class VecLdSbFwdIO(implicit p: Parameters) extends Bundle {
  private val n = p(FunctionalUnits).count(_.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD)
  val ports     = Vec(n, new LdSbFwdIO)
}

class VecStSbWriteIO(implicit p: Parameters) extends Bundle {
  private val n = p(FunctionalUnits).count(_.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST)
  val ports     = Vec(n, new StSbWriteIO)
}

class VecBruResolveIO(implicit p: Parameters) extends Bundle {
  private val n = p(FunctionalUnits).count(_.`type` == FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_BRU)
  val ports     = Vec(n, new BruResolveIO)
}

class FuPoolIO(implicit p: Parameters) extends Bundle {
  val fu     = new FuPoolFuIO
  val ld_mem = new VecLdMemIO
  val ld_sb  = new VecLdSbFwdIO
  val st_sb  = new VecStSbWriteIO
  val bru    = new VecBruResolveIO
}

class FuPool(implicit p: Parameters) extends Node(new FuPoolIO) {
  override def nodeType: NodeType  = FuPoolMeta.Type
  override def desiredName: String = s"fu_pool_${p(ISA).name}"

  private def build(desc: arch.core.fu.FunctionalUnitDescriptor): Node[_ <: Bundle] =
    desc.`type` match {
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ALU  => Module(new Alu)
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_MULT => Module(new Mult)
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_DIV  => Module(new Div)
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_LD   => Module(new Ld)
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_ST   => Module(new St)
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_BRU  => Module(new Bru)
      case FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR  =>
        throw new UnsupportedOperationException("FuPool: CSR node is not implemented yet")
      case other                                        =>
        throw new UnsupportedOperationException(
          s"FuPool: unsupported FU type '${other.cppName}' for '${desc.name}'"
        )
    }

  for (i <- 0 until p(NumFUs)) {
    io.fu.req(i).ready  := false.B
    io.fu.done(i).valid := false.B
    io.fu.done(i).bits  := 0.U.asTypeOf(new FuResp)
  }

  private val activeDescs = p(FunctionalUnits).zipWithIndex.filter { case (desc, _) =>
    desc.`type` != FunctionalUnitType.FUNCTIONAL_UNIT_TYPE_CSR
  }

  private val units = activeDescs.map { case (desc, idx) =>
    build(desc) -> idx
  }

  private def connectFu(fu: FuIO, idx: Int): Unit = {
    fu.flush              := io.fu.flush
    fu.req <> io.fu.req(idx)
    fu.resp.ready         := true.B
    io.fu.done(idx).valid := fu.resp.valid
    io.fu.done(idx).bits  := fu.resp.bits
  }

  private var ldIdx  = 0
  private var stIdx  = 0
  private var bruIdx = 0

  for ((unit, fuIdx) <- units)
    unit match {
      case alu: Alu =>
        connectFu(alu.io.fu, fuIdx)

      case mult: Mult =>
        connectFu(mult.io.fu, fuIdx)

      case div: Div =>
        connectFu(div.io.fu, fuIdx)

      case ld: Ld =>
        connectFu(ld.io.fu, fuIdx)
        ld.io.mem <> io.ld_mem.ports(ldIdx)
        ld.io.sb <> io.ld_sb.ports(ldIdx)
        ldIdx += 1

      case st: St =>
        connectFu(st.io.fu, fuIdx)
        st.io.sb <> io.st_sb.ports(stIdx)
        stIdx += 1

      case bru: Bru =>
        connectFu(bru.io.fu, fuIdx)
        io.bru.ports(bruIdx).resolved := bru.io.resolve.resolved
        bruIdx += 1

      case _ =>
    }

  require(
    ldIdx == io.ld_mem.ports.length,
    s"FuPool: connected $ldIdx LD nodes, expected ${io.ld_mem.ports.length}"
  )

  require(
    stIdx == io.st_sb.ports.length,
    s"FuPool: connected $stIdx ST nodes, expected ${io.st_sb.ports.length}"
  )

  require(
    bruIdx == io.bru.ports.length,
    s"FuPool: connected $bruIdx BRU nodes, expected ${io.bru.ports.length}"
  )
}
