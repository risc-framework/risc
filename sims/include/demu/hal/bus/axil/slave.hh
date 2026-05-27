#pragma once

#include "demu/hal/device.hh"
#include <queue>

namespace demu::hal::axil {

class AXILiteSlave : public Device {
public:
  explicit AXILiteSlave(const sys_def::DeviceDescriptor &desc) : Device(desc) {}

  ~AXILiteSlave() override = default;

  // AW
  virtual void aw_valid(addr_t addr) { _write_addr_queue.push(addr); };
  [[nodiscard]] virtual auto aw_ready() const noexcept -> bool { return true; };

  // W
  virtual void w_valid(word_t data, byte_t strb) {
    _write_data_queue.push({data, strb});
  }
  [[nodiscard]] virtual auto w_ready() const noexcept -> bool { return true; }

  // B
  [[nodiscard]] virtual auto b_valid() const noexcept -> bool {
    return !_write_resp_queue.empty();
  }
  virtual void b_ready(bool ready) {
    if (ready && !_write_resp_queue.empty()) {
      _write_resp_queue.pop();
    }
  }
  [[nodiscard]] virtual auto b_resp() const noexcept -> uint8_t {
    return _write_resp_queue.empty() ? 0u : _write_resp_queue.front().resp;
  }

  // AR
  virtual void ar_valid(addr_t addr) { _read_queue.push({addr, 0u, false}); }
  [[nodiscard]] virtual auto ar_ready() const noexcept -> bool { return true; }

  // R
  [[nodiscard]] virtual auto r_valid() const noexcept -> bool {
    return !_read_queue.empty() && _read_queue.front().processed;
  }
  virtual void r_ready(bool ready) {
    if (ready && r_valid()) {
      _read_queue.pop();
    }
  }
  [[nodiscard]] virtual auto r_data() const noexcept -> word_t {
    return _read_queue.empty() ? 0u : _read_queue.front().data;
  }
  [[nodiscard]] virtual auto r_resp() const noexcept -> uint8_t { return 0u; }

protected:
  struct WriteData {
    word_t data;
    byte_t strb;
  };
  struct WriteResponse {
    uint8_t resp;
  };
  struct ReadTransaction {
    addr_t addr;
    word_t data;
    bool processed;
  };

  std::queue<addr_t> _write_addr_queue;
  std::queue<WriteData> _write_data_queue;
  std::queue<WriteResponse> _write_resp_queue;
  std::queue<ReadTransaction> _read_queue;
};

} // namespace demu::hal::axil
