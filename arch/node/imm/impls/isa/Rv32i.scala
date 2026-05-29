package arch.node.imm.impls.isa.rv32i

import arch.node.imm.{ ImmIsaImpl, ImmIsaFactory }
import vutils.graph.{ RegisteredNodeUtils, NodeRegistry }
import chisel3._
import chisel3.util.{ BitPat, MuxLookup, Cat, Fill }

trait Rv32iImmConsts {
  def IMM_X   = BitPat("b???")
  def SZ_IMM  = IMM_X.getWidth
  def IMM_I   = BitPat("b000")
  def IMM_S   = BitPat("b001")
  def IMM_B   = BitPat("b010")
  def IMM_U   = BitPat("b011")
  def IMM_J   = BitPat("b100")
  def IMM_CSR = BitPat("b101")
}

object ImmRv32iIsa extends RegisteredNodeUtils[ImmIsaImpl] with Rv32iImmConsts {
  override def utils: ImmIsaImpl = new ImmIsaImpl {
    override def value: String = "rv32i"

    override def immTypeWidth: Int = SZ_IMM

    override def gen(instr: UInt, immType: UInt): UInt =
      MuxLookup(immType, 0.U(SZ_IMM.W))(
        Seq(
          IMM_I.value.U(SZ_IMM.W)   -> Cat(Fill(20, instr(31)), instr(31, 20)),
          IMM_S.value.U(SZ_IMM.W)   -> Cat(Fill(20, instr(31)), instr(31, 25), instr(11, 7)),
          IMM_B.value.U(SZ_IMM.W)   -> Cat(
            Fill(19, instr(31)),
            instr(31),
            instr(7),
            instr(30, 25),
            instr(11, 8),
            0.U(1.W)
          ),
          IMM_U.value.U(SZ_IMM.W)   -> Cat(instr(31, 12), Fill(12, 0.U)),
          IMM_J.value.U(SZ_IMM.W)   -> Cat(
            Fill(11, instr(31)),
            instr(31),
            instr(19, 12),
            instr(20),
            instr(30, 21),
            0.U(1.W)
          ),
          IMM_CSR.value.U(SZ_IMM.W) -> Cat(Fill(27, 0.U), instr(19, 15))
        )
      )
  }

  override def registry: NodeRegistry[ImmIsaImpl] = ImmIsaFactory
}
