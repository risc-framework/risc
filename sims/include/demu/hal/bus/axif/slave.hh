#pragma once

#include "../../device.hh"
#include <queue>

namespace demu::hal::axif {
using namespace isa;

class AXIFullSlave : public Device {
public:
  explicit AXIFullSlave(const sys_def::DeviceDescriptor &desc) : Device(desc) {}
  ~AXIFullSlave() override = default;

  // AW
  virtual void aw_valid(bool valid, uint8_t id, addr_t addr, uint8_t len,
                        uint8_t size, uint8_t burst) {
    pin_awvalid = valid;
    pin_awid = id;
    pin_awaddr = addr;
    pin_awlen = len;
    pin_awsize = size;
    pin_awburst = burst;
  }
  virtual auto aw_ready() const noexcept -> bool { return true; }

  // W
  virtual void w_valid(bool valid, word_t data, byte_t strb, bool last) {
    pin_wvalid = valid;
    pin_wdata = data;
    pin_wstrb = strb;
    pin_wlast = last;
  }
  virtual auto w_ready() const noexcept -> bool { return true; }

  // B
  virtual void b_ready(bool ready) { pin_bready = ready; }
  virtual auto b_valid() const noexcept -> bool {
    return !_write_resp_queue.empty();
  }
  virtual auto b_resp() const noexcept -> uint8_t {
    return b_valid() ? _write_resp_queue.front().resp : 0;
  }
  virtual auto b_id() const noexcept -> uint8_t {
    return b_valid() ? _write_resp_queue.front().id : 0;
  }

  // AR
  virtual void ar_valid(bool valid, uint8_t id, addr_t addr, uint8_t len,
                        uint8_t size, uint8_t burst) {
    pin_arvalid = valid;
    pin_arid = id;
    pin_araddr = addr;
    pin_arlen = len;
    pin_arsize = size;
    pin_arburst = burst;
  }
  virtual auto ar_ready() const noexcept -> bool { return true; }

  // R
  virtual void r_ready(bool ready) { pin_rready = ready; }
  virtual auto r_valid() const noexcept -> bool {
    return !_read_data_queue.empty();
  }
  virtual auto r_data() const noexcept -> word_t {
    return r_valid() ? _read_data_queue.front().data : 0;
  }
  virtual auto r_resp() const noexcept -> uint8_t {
    return r_valid() ? _read_data_queue.front().resp : 0;
  }
  virtual auto r_id() const noexcept -> uint32_t {
    return r_valid() ? _read_data_queue.front().id : 0;
  }
  virtual auto r_last() const noexcept -> bool {
    return r_valid() ? _read_data_queue.front().last : false;
  }

protected:
  // Cached Pin States
  bool pin_awvalid{false};
  uint8_t pin_awid{0};
  addr_t pin_awaddr{0};
  uint8_t pin_awlen{0};
  uint8_t pin_awsize{0};
  uint8_t pin_awburst{0};

  bool pin_wvalid{false};
  word_t pin_wdata{0};
  byte_t pin_wstrb{0};
  bool pin_wlast{false};

  bool pin_bready{false};

  bool pin_arvalid{false};
  uint8_t pin_arid{0};
  addr_t pin_araddr{0};
  uint8_t pin_arlen{0};
  uint8_t pin_arsize{0};
  uint8_t pin_arburst{0};

  bool pin_rready{false};

  struct BurstTransaction {
    uint8_t id;
    addr_t addr;
    uint8_t len;
    uint8_t size;
    uint8_t burst;
    uint8_t beats;
  };
  struct WriteData {
    word_t data;
    byte_t strb;
    bool last;
  };
  struct WriteResponse {
    uint8_t id;
    uint8_t resp;
  };
  struct ReadData {
    uint8_t id;
    word_t data;
    uint8_t resp;
    bool last;
  };

  std::queue<BurstTransaction> _write_req_queue;
  std::queue<WriteData> _write_data_queue;
  std::queue<WriteResponse> _write_resp_queue;
  std::queue<BurstTransaction> _read_req_queue;
  std::queue<ReadData> _read_data_queue;
};

} // namespace demu::hal::axif
