package arch.cpp

import arch.configs._

private[cpp] object CppBusBindingsSchema {
  def emitIncludes(w: CppWriter, p: Parameters): Unit =
    p(BusType) match {
      case "axif" =>
        w.line("#include \"demu/hal/bus/axif/port_handler.hh\"")
      case "axil" =>
        w.line("#include \"demu/hal/bus/axil/port_handler.hh\"")
      case other  =>
        throw new IllegalArgumentException(
          s"CppBusBindingsSchema: unsupported bus type '$other'"
        )
    }

  def emit(w: CppWriter, p: Parameters): Unit = {
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

  private def emitForwardDecls(w: CppWriter): Unit = {
    w.line("template <size_t PortID>")
    w.line("struct AXIFPortBinding;")
    w.line()
    w.line("template <size_t PortID>")
    w.line("struct AXILPortBinding;")
  }

  private def emitAxif(w: CppWriter, p: Parameters): Unit =
    for (port <- p(BusAddressMap).indices) {
      emitAxifPort(w, port)
      w.line()
    }

  private def emitAxifPort(w: CppWriter, port: Int): Unit = {
    val pid = port.toString

    w.line(s"template <>")
    w.line(s"struct AXIFPortBinding<$pid> {")
    w.indent {
      w.line("static auto bind(::demu::isa_def::system_t *dut)")
      w.line("    -> ::demu::hal::axif::AXIFullSignals {")
      w.indent {
        w.line("::demu::hal::axif::AXIFullSignals s{};")

        bind(w, "awid", s"M_AXIF_${pid}_AWID")
        bind(w, "awaddr", s"M_AXIF_${pid}_AWADDR")
        bind(w, "awlen", s"M_AXIF_${pid}_AWLEN")
        bind(w, "awsize", s"M_AXIF_${pid}_AWSIZE")
        bind(w, "awburst", s"M_AXIF_${pid}_AWBURST")
        bind(w, "awvalid", s"M_AXIF_${pid}_AWVALID")
        bind(w, "awready", s"M_AXIF_${pid}_AWREADY")

        bind(w, "wdata", s"M_AXIF_${pid}_WDATA")
        bind(w, "wstrb", s"M_AXIF_${pid}_WSTRB")
        bind(w, "wlast", s"M_AXIF_${pid}_WLAST")
        bind(w, "wvalid", s"M_AXIF_${pid}_WVALID")
        bind(w, "wready", s"M_AXIF_${pid}_WREADY")

        bind(w, "bid", s"M_AXIF_${pid}_BID")
        bind(w, "bresp", s"M_AXIF_${pid}_BRESP")
        bind(w, "bvalid", s"M_AXIF_${pid}_BVALID")
        bind(w, "bready", s"M_AXIF_${pid}_BREADY")

        bind(w, "arid", s"M_AXIF_${pid}_ARID")
        bind(w, "araddr", s"M_AXIF_${pid}_ARADDR")
        bind(w, "arlen", s"M_AXIF_${pid}_ARLEN")
        bind(w, "arsize", s"M_AXIF_${pid}_ARSIZE")
        bind(w, "arburst", s"M_AXIF_${pid}_ARBURST")
        bind(w, "arvalid", s"M_AXIF_${pid}_ARVALID")
        bind(w, "arready", s"M_AXIF_${pid}_ARREADY")

        bind(w, "rid", s"M_AXIF_${pid}_RID")
        bind(w, "rdata", s"M_AXIF_${pid}_RDATA")
        bind(w, "rresp", s"M_AXIF_${pid}_RRESP")
        bind(w, "rlast", s"M_AXIF_${pid}_RLAST")
        bind(w, "rvalid", s"M_AXIF_${pid}_RVALID")
        bind(w, "rready", s"M_AXIF_${pid}_RREADY")

        w.line("return s;")
      }
      w.line("}")
    }
    w.line("};")
  }

  private def emitAxil(w: CppWriter, p: Parameters): Unit =
    for (port <- p(BusAddressMap).indices) {
      emitAxilPort(w, port)
      w.line()
    }

  private def emitAxilPort(w: CppWriter, port: Int): Unit = {
    val pid = port.toString

    w.line(s"template <>")
    w.line(s"struct AXILPortBinding<$pid> {")
    w.indent {
      w.line("static auto bind(::demu::isa_def::system_t *dut)")
      w.line("    -> ::demu::hal::axil::AXILiteSignals {")
      w.indent {
        w.line("::demu::hal::axil::AXILiteSignals s{};")

        bind(w, "awaddr", s"M_AXIL_${pid}_AWADDR")
        bind(w, "awprot", s"M_AXIL_${pid}_AWPROT")
        bind(w, "awvalid", s"M_AXIL_${pid}_AWVALID")
        bind(w, "awready", s"M_AXIL_${pid}_AWREADY")

        bind(w, "wdata", s"M_AXIL_${pid}_WDATA")
        bind(w, "wstrb", s"M_AXIL_${pid}_WSTRB")
        bind(w, "wvalid", s"M_AXIL_${pid}_WVALID")
        bind(w, "wready", s"M_AXIL_${pid}_WREADY")

        bind(w, "bresp", s"M_AXIL_${pid}_BRESP")
        bind(w, "bvalid", s"M_AXIL_${pid}_BVALID")
        bind(w, "bready", s"M_AXIL_${pid}_BREADY")

        bind(w, "araddr", s"M_AXIL_${pid}_ARADDR")
        bind(w, "arprot", s"M_AXIL_${pid}_ARPROT")
        bind(w, "arvalid", s"M_AXIL_${pid}_ARVALID")
        bind(w, "arready", s"M_AXIL_${pid}_ARREADY")

        bind(w, "rdata", s"M_AXIL_${pid}_RDATA")
        bind(w, "rresp", s"M_AXIL_${pid}_RRESP")
        bind(w, "rvalid", s"M_AXIL_${pid}_RVALID")
        bind(w, "rready", s"M_AXIL_${pid}_RREADY")

        w.line("return s;")
      }
      w.line("}")
    }
    w.line("};")
  }

  private def bind(w: CppWriter, field: String, signal: String): Unit =
    w.line(s"s.$field = &dut->$signal;")
}
