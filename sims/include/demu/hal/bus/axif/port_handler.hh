#pragma once

#include "../../port_handler.hh"
#include "./slave.hh"
#include <cstdint>

namespace demu::hal::axif {

struct AXIFullSignals {
  uint8_t *awid{};
  addr_t *awaddr{};
  uint8_t *awlen{};
  uint8_t *awsize{};
  uint8_t *awburst{};
  uint8_t *awvalid{};
  uint8_t *awready{};

  word_t *wdata{};
  uint8_t *wstrb{};
  uint8_t *wlast{};
  uint8_t *wvalid{};
  uint8_t *wready{};

  uint8_t *bid{};
  uint8_t *bresp{};
  uint8_t *bvalid{};
  uint8_t *bready{};

  uint8_t *arid{};
  addr_t *araddr{};
  uint8_t *arlen{};
  uint8_t *arsize{};
  uint8_t *arburst{};
  uint8_t *arvalid{};
  uint8_t *arready{};

  uint8_t *rid{};
  word_t *rdata{};
  uint8_t *rresp{};
  uint8_t *rlast{};
  uint8_t *rvalid{};
  uint8_t *rready{};

  [[nodiscard]] auto valid() const noexcept -> bool {
    return awid && awaddr && awlen && awsize && awburst && awvalid && awready &&
           wdata && wstrb && wlast && wvalid && wready && bid && bresp &&
           bvalid && bready && arid && araddr && arlen && arsize && arburst &&
           arvalid && arready && rid && rdata && rresp && rlast && rvalid &&
           rready;
  }
};

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
