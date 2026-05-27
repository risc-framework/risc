#pragma once

#include "../../port_handler.hh"
#include "./slave.hh"
#include "demu/generated/bus_bindings.hh"

namespace demu::hal::axif {

using sys_def::AXIFullSignals;

class AXIFullPortHandler final : public hal::PortHandler {
public:
  explicit AXIFullPortHandler(AXIFullSignals signals) : signals_(signals) {}

  static constexpr auto static_protocol_name() noexcept -> const char * {
    return "AXI4-Full";
  }

  void handle(hal::Hardware *hw) noexcept override {
    auto *slave = dynamic_cast<AXIFullSlave *>(hw);
    if (!slave || !signals_.valid()) {
      return;
    }

    auto &s = signals_;

    slave->aw_valid(*s.awvalid, *s.awid, *s.awaddr, *s.awlen, *s.awsize,
                    *s.awburst);
    slave->w_valid(*s.wvalid, *s.wdata, *s.wstrb, *s.wlast);
    slave->b_ready(*s.bready);

    slave->ar_valid(*s.arvalid, *s.arid, *s.araddr, *s.arlen, *s.arsize,
                    *s.arburst);
    slave->r_ready(*s.rready);

    *s.awready = slave->aw_ready();
    *s.wready = slave->w_ready();

    *s.bvalid = slave->b_valid();
    *s.bresp = slave->b_resp();
    *s.bid = slave->b_id();

    *s.arready = slave->ar_ready();

    *s.rvalid = slave->r_valid();
    *s.rdata = slave->r_data();
    *s.rresp = slave->r_resp();
    *s.rid = slave->r_id();
    *s.rlast = slave->r_last();
  }

  [[nodiscard]] auto protocol_name() const noexcept -> const char * override {
    return static_protocol_name();
  }

private:
  AXIFullSignals signals_;
};

} // namespace demu::hal::axif
