#pragma once

#include "../../port_handler.hh"
#include "./slave.hh"
#include "demu/generated/bus_bindings.hh"

namespace demu::hal::axil {

using sys_def::AXILiteSignals;

class AXILitePortHandler final : public hal::PortHandler {
public:
  explicit AXILitePortHandler(AXILiteSignals signals) : signals_(signals) {}

  static constexpr auto static_protocol_name() noexcept -> const char * {
    return "AXI4-Lite";
  }

  void handle(hal::Hardware *hw) noexcept override {
    auto *slave = dynamic_cast<AXILiteSlave *>(hw);
    if (!slave || !signals_.valid()) {
      return;
    }

    auto &s = signals_;

    if (*s.awvalid && slave->aw_ready()) {
      slave->aw_valid(*s.awaddr);
    }
    *s.awready = slave->aw_ready();

    if (*s.wvalid && slave->w_ready()) {
      slave->w_valid(*s.wdata, *s.wstrb & 0xF);
    }
    *s.wready = slave->w_ready();

    *s.bvalid = slave->b_valid();
    *s.bresp = slave->b_resp();
    slave->b_ready(*s.bready);

    if (*s.arvalid && slave->ar_ready()) {
      slave->ar_valid(*s.araddr);
    }
    *s.arready = slave->ar_ready();

    *s.rvalid = slave->r_valid();
    *s.rdata = slave->r_data();
    *s.rresp = slave->r_resp();
    slave->r_ready(*s.rready);
  }

  [[nodiscard]] auto protocol_name() const noexcept -> const char * override {
    return static_protocol_name();
  }

private:
  AXILiteSignals signals_;
};

} // namespace demu::hal::axil
