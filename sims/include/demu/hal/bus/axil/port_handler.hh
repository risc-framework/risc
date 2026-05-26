#pragma once

#include "../../port_handler.hh"
#include "./slave.hh"
#include <cstdint>

namespace demu::hal::axil {

struct AXILiteSignals {
  addr_t *awaddr{};
  uint8_t *awprot{};
  uint8_t *awvalid{};
  uint8_t *awready{};

  word_t *wdata{};
  uint8_t *wstrb{};
  uint8_t *wvalid{};
  uint8_t *wready{};

  uint8_t *bresp{};
  uint8_t *bvalid{};
  uint8_t *bready{};

  addr_t *araddr{};
  uint8_t *arprot{};
  uint8_t *arvalid{};
  uint8_t *arready{};

  word_t *rdata{};
  uint8_t *rresp{};
  uint8_t *rvalid{};
  uint8_t *rready{};

  [[nodiscard]] auto valid() const noexcept -> bool {
    return awaddr && awprot && awvalid && awready && wdata && wstrb && wvalid &&
           wready && bresp && bvalid && bready && araddr && arprot && arvalid &&
           arready && rdata && rresp && rvalid && rready;
  }
};

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
