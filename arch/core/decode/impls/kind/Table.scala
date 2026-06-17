package arch.core.decode.impls.kind.table

import arch.configs._
import arch.core.bpu.BpuBranchKind
import arch.core.decode._
import chisel3._
import vutils.graph.{ NodeDimensionRegistry, RegisteredNodeUtils }

object DecodeTableKind extends RegisteredNodeUtils[DecodeKindImpl] {
  override def utils: DecodeKindImpl = new DecodeKindImpl {
    override def value: String = "table"

    override def decode(isa: DecodeIsaImpl, in: DecodePacket)(implicit
      p: Parameters
    ): DecodedPacket = {
      val out     = WireDefault(0.U.asTypeOf(new DecodedPacket))
      val decoded = DecodeLogic(in.instr, isa.default, isa.table)

      val rs1 = isa.reg(decoded(8), in.instr)
      val rs2 = isa.reg(decoded(9), in.instr)
      val rd  = isa.reg(decoded(10), in.instr)
      val imm = isa.imm(decoded(11), in.instr)

      val opcode = in.instr(6, 0)
      val rs1Raw = in.instr(19, 15)
      val rdRaw  = in.instr(11, 7)
      val isLinkRd =
        rdRaw === 1.U || rdRaw === 5.U
      val isLinkRs1 =
        rs1Raw === 1.U || rs1Raw === 5.U
      val isSameLink = rdRaw === rs1Raw
      val isJal    = opcode === "b1101111".U
      val isJalr   = opcode === "b1100111".U
      val isBranch = opcode === "b1100011".U

      out.pc               := in.pc
      out.instr            := in.instr
      out.bpu_pred_taken   := in.bpu_pred_taken
      out.bpu_pred_target  := in.bpu_pred_target
      out.bpu_pht_index    := in.bpu_pht_index
      out.bpu_ghr_snapshot := in.bpu_ghr_snapshot
      out.bpu_branch_kind  := Mux(
        isBranch,
        BpuBranchKind.BRANCH,
        Mux(
          isJal && isLinkRd,
          BpuBranchKind.CALL,
          Mux(
            isJal,
            BpuBranchKind.JUMP,
            Mux(
              isJalr && !isLinkRd && isLinkRs1,
              BpuBranchKind.RET,
              Mux(
                isJalr && isLinkRd && isLinkRs1 && !isSameLink,
                BpuBranchKind.CALL_RET,
                Mux(isJalr && isLinkRd, BpuBranchKind.CALL, BpuBranchKind.NONE)
              )
            )
          )
        )
      )

      out.legal          := decoded(0).asBool
      out.regwrite       := decoded(1).asBool
      out.rs1_read       := decoded(2).asBool && isa.readable(rs1)
      out.rs2_read       := decoded(3).asBool && isa.readable(rs2)
      out.rd_write       := decoded(4).asBool && isa.writable(rd)
      out.commit_barrier := decoded(5).asBool

      out.fu_type := decoded(6)
      out.uop     := decoded(7)

      out.rs1 := rs1
      out.rs2 := rs2
      out.rd  := rd
      out.imm := imm

      out
    }
  }

  override def registry: NodeDimensionRegistry[DecodeKindImpl] =
    DecodeKindFactory
}
