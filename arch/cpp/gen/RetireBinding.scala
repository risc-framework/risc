package arch.cpp.gen

import arch.configs._
import arch.cpp.dsl.CppWriter
import arch.cpp.dsl.CppStructDsl._
import arch.cpp.dsl.CppTypeDsl._

private[cpp] object CppRetireBindingsSchema {
  private val retirePacketFields: Seq[StructField] = Seq(
    field(boolean, "valid"),
    field(addr, "pc"),
    field(instr, "instr"),
    field(boolean, "reg_we"),
    field(u8, "reg_addr"),
    field(word, "reg_data"),
  )

  def emit(w: CppWriter, p: Parameters): Unit = {
    emitRetirePacket(w)
    w.line()

    w.line(s"inline constexpr uint32_t NUM_RETIRE_LANES = ${p(IssueWidth)}u;")
    w.line()

    emitForwardDecl(w)
    w.line()

    for (lane <- 0 until p(IssueWidth)) {
      emitLaneBinding(w, lane)
      w.line()
    }

    emitReadFunction(w, p)
  }

  private def emitRetirePacket(w: CppWriter): Unit =
    StructSpec(
      name = "RetirePacket",
      fields = retirePacketFields,
      emitValidMethod = false
    ).emit(w)

  private def emitForwardDecl(w: CppWriter): Unit = {
    w.line("template <size_t LaneID>")
    w.line("struct RetireLaneBinding;")
  }

  private def emitLaneBinding(w: CppWriter, lane: Int): Unit = {
    val id = lane.toString

    w.line("template <>")
    w.line(s"struct RetireLaneBinding<$id> {")
    w.indent {
      w.line("static auto read(const ::demu::isa_def::soc_t *dut) noexcept")
      w.line("    -> RetirePacket {")
      w.indent {
        w.line("RetirePacket packet{};")
        w.line(s"packet.valid = static_cast<bool>(dut->debug_instret_$id);")
        w.line(s"packet.pc = static_cast<::demu::isa_def::addr_t>(dut->debug_pc_$id);")
        w.line(s"packet.instr = static_cast<::demu::isa_def::instr_t>(dut->debug_instr_$id);")
        w.line(s"packet.reg_we = static_cast<bool>(dut->debug_reg_we_$id);")
        w.line(s"packet.reg_addr = static_cast<uint8_t>(dut->debug_reg_addr_$id);")
        w.line(s"packet.reg_data = static_cast<::demu::isa_def::word_t>(dut->debug_reg_data_$id);")
        w.line("return packet;")
      }
      w.line("}")
    }
    w.line("};")
  }

  private def emitReadFunction(w: CppWriter, p: Parameters): Unit = {
    w.line("inline auto read(const ::demu::isa_def::soc_t *dut, uint32_t lane) noexcept")
    w.line("    -> RetirePacket {")
    w.indent {
      w.line("switch (lane) {")
      w.indent {
        for (lane <- 0 until p(IssueWidth)) {
          w.line(s"case $lane:")
          w.indent {
            w.line(s"return RetireLaneBinding<$lane>::read(dut);")
          }
        }

        w.line("default:")
        w.indent {
          w.line("return {};")
        }
      }
      w.line("}")
    }
    w.line("}")
  }
}
