package arch.cpp

import arch.configs._
import CppStructDsl._
import CppTypeDsl._

private[cpp] object CppBusBindingsSchema {
  private val axifSignals: Seq[StructField] = Seq(
    field(u8.ptr, "awid"),
    field(addr.ptr, "awaddr"),
    field(u8.ptr, "awlen"),
    field(u8.ptr, "awsize"),
    field(u8.ptr, "awburst"),
    field(u8.ptr, "awvalid"),
    field(u8.ptr, "awready"),
    field(word.ptr, "wdata"),
    field(u8.ptr, "wstrb"),
    field(u8.ptr, "wlast"),
    field(u8.ptr, "wvalid"),
    field(u8.ptr, "wready"),
    field(u8.ptr, "bid"),
    field(u8.ptr, "bresp"),
    field(u8.ptr, "bvalid"),
    field(u8.ptr, "bready"),
    field(u8.ptr, "arid"),
    field(addr.ptr, "araddr"),
    field(u8.ptr, "arlen"),
    field(u8.ptr, "arsize"),
    field(u8.ptr, "arburst"),
    field(u8.ptr, "arvalid"),
    field(u8.ptr, "arready"),
    field(u8.ptr, "rid"),
    field(word.ptr, "rdata"),
    field(u8.ptr, "rresp"),
    field(u8.ptr, "rlast"),
    field(u8.ptr, "rvalid"),
    field(u8.ptr, "rready"),
  )

  private val axilSignals: Seq[StructField] = Seq(
    field(addr.ptr, "awaddr"),
    field(u8.ptr, "awprot"),
    field(u8.ptr, "awvalid"),
    field(u8.ptr, "awready"),
    field(word.ptr, "wdata"),
    field(u8.ptr, "wstrb"),
    field(u8.ptr, "wvalid"),
    field(u8.ptr, "wready"),
    field(u8.ptr, "bresp"),
    field(u8.ptr, "bvalid"),
    field(u8.ptr, "bready"),
    field(addr.ptr, "araddr"),
    field(u8.ptr, "arprot"),
    field(u8.ptr, "arvalid"),
    field(u8.ptr, "arready"),
    field(word.ptr, "rdata"),
    field(u8.ptr, "rresp"),
    field(u8.ptr, "rvalid"),
    field(u8.ptr, "rready"),
  )

  def emit(w: CppWriter, p: Parameters): Unit = {
    emitSignalTypes(w)
    w.line()

    emitForwardDecls(w)
    w.line()

    p(BusType) match {
      case "axif" => emitAxif(w, p)
      case "axil" => emitAxil(w, p)
      case other  =>
        throw new IllegalArgumentException(
          s"CppBusBindingsSchema: unsupported bus type '$other'"
        )
    }
  }

  private def emitSignalTypes(w: CppWriter): Unit = {
    StructSpec(
      name = "AXIFullSignals",
      fields = axifSignals,
      emitValidMethod = true
    ).emit(w)

    w.line()

    StructSpec(
      name = "AXILiteSignals",
      fields = axilSignals,
      emitValidMethod = true
    ).emit(w)
  }

  private def emitForwardDecls(w: CppWriter): Unit = {
    w.line("template <size_t PortID>")
    w.line("struct AXIFPortBinding;")
    w.line()
    w.line("template <size_t PortID>")
    w.line("struct AXILPortBinding;")
  }

  private def emitAxif(w: CppWriter, p: Parameters): Unit =
    for (port <- p(BusAddressMap).indices) {
      emitBusPort(
        w = w,
        port = port,
        bindingName = "AXIFPortBinding",
        signalType = "AXIFullSignals",
        rtlPrefix = "M_AXIF",
        fields = axifSignals,
      )

      w.line()
    }

  private def emitAxil(w: CppWriter, p: Parameters): Unit =
    for (port <- p(BusAddressMap).indices) {
      emitBusPort(
        w = w,
        port = port,
        bindingName = "AXILPortBinding",
        signalType = "AXILiteSignals",
        rtlPrefix = "M_AXIL",
        fields = axilSignals,
      )

      w.line()
    }

  private def emitBusPort(
    w: CppWriter,
    port: Int,
    bindingName: String,
    signalType: String,
    rtlPrefix: String,
    fields: Seq[StructField]
  ): Unit = {
    val pid = port.toString

    w.line("template <>")
    w.line(s"struct $bindingName<$pid> {")
    w.indent {
      w.line(s"static auto bind(::demu::isa_def::soc_t *dut) -> $signalType {")
      w.indent {
        w.line(s"$signalType s{};")

        fields.foreach { f =>
          bind(w, f.name, s"${rtlPrefix}_${pid}_${f.name.toUpperCase}")
        }

        w.line("return s;")
      }
      w.line("}")
    }
    w.line("};")
  }

  private def bind(w: CppWriter, field: String, signal: String): Unit =
    w.line(s"s.$field = &dut->$signal;")
}
