package arch.cpp

import arch.configs._

private[cpp] object CppBusBindingsSchema {
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
    w.line("struct AXIFullSignals {")
    w.indent {
      w.line("uint8_t *awid{};")
      w.line("::demu::isa_def::addr_t *awaddr{};")
      w.line("uint8_t *awlen{};")
      w.line("uint8_t *awsize{};")
      w.line("uint8_t *awburst{};")
      w.line("uint8_t *awvalid{};")
      w.line("uint8_t *awready{};")
      w.line()
      w.line("::demu::isa_def::word_t *wdata{};")
      w.line("uint8_t *wstrb{};")
      w.line("uint8_t *wlast{};")
      w.line("uint8_t *wvalid{};")
      w.line("uint8_t *wready{};")
      w.line()
      w.line("uint8_t *bid{};")
      w.line("uint8_t *bresp{};")
      w.line("uint8_t *bvalid{};")
      w.line("uint8_t *bready{};")
      w.line()
      w.line("uint8_t *arid{};")
      w.line("::demu::isa_def::addr_t *araddr{};")
      w.line("uint8_t *arlen{};")
      w.line("uint8_t *arsize{};")
      w.line("uint8_t *arburst{};")
      w.line("uint8_t *arvalid{};")
      w.line("uint8_t *arready{};")
      w.line()
      w.line("uint8_t *rid{};")
      w.line("::demu::isa_def::word_t *rdata{};")
      w.line("uint8_t *rresp{};")
      w.line("uint8_t *rlast{};")
      w.line("uint8_t *rvalid{};")
      w.line("uint8_t *rready{};")
      w.line()
      w.line("[[nodiscard]] auto valid() const noexcept -> bool {")
      w.indent {
        w.line("return awid && awaddr && awlen && awsize && awburst &&")
        w.line("       awvalid && awready && wdata && wstrb && wlast &&")
        w.line("       wvalid && wready && bid && bresp && bvalid && bready &&")
        w.line("       arid && araddr && arlen && arsize && arburst &&")
        w.line("       arvalid && arready && rid && rdata && rresp &&")
        w.line("       rlast && rvalid && rready;")
      }
      w.line("}")
    }
    w.line("};")
    w.line()

    w.line("struct AXILiteSignals {")
    w.indent {
      w.line("::demu::isa_def::addr_t *awaddr{};")
      w.line("uint8_t *awprot{};")
      w.line("uint8_t *awvalid{};")
      w.line("uint8_t *awready{};")
      w.line()
      w.line("::demu::isa_def::word_t *wdata{};")
      w.line("uint8_t *wstrb{};")
      w.line("uint8_t *wvalid{};")
      w.line("uint8_t *wready{};")
      w.line()
      w.line("uint8_t *bresp{};")
      w.line("uint8_t *bvalid{};")
      w.line("uint8_t *bready{};")
      w.line()
      w.line("::demu::isa_def::addr_t *araddr{};")
      w.line("uint8_t *arprot{};")
      w.line("uint8_t *arvalid{};")
      w.line("uint8_t *arready{};")
      w.line()
      w.line("::demu::isa_def::word_t *rdata{};")
      w.line("uint8_t *rresp{};")
      w.line("uint8_t *rvalid{};")
      w.line("uint8_t *rready{};")
      w.line()
      w.line("[[nodiscard]] auto valid() const noexcept -> bool {")
      w.indent {
        w.line("return awaddr && awprot && awvalid && awready &&")
        w.line("       wdata && wstrb && wvalid && wready &&")
        w.line("       bresp && bvalid && bready && araddr && arprot &&")
        w.line("       arvalid && arready && rdata && rresp && rvalid &&")
        w.line("       rready;")
      }
      w.line("}")
    }
    w.line("};")
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
      w.line("static auto bind(::demu::isa_def::system_t *dut) -> AXIFullSignals {")
      w.indent {
        w.line("AXIFullSignals s{};")

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
      w.line("static auto bind(::demu::isa_def::system_t *dut) -> AXILiteSignals {")
      w.indent {
        w.line("AXILiteSignals s{};")

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
