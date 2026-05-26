#pragma once

#include "../isa/isa.hh"

namespace demu::hal {

class Hardware {
public:
  virtual ~Hardware() = default;

  virtual void clock_tick() = 0;
  virtual void reset() = 0;

  [[nodiscard]] virtual auto name() const noexcept -> const char * {
    return "Unknown Hardware";
  }
};

template <typename DUT> class RTLHardware : public Hardware {
public:
  RTLHardware() : dut_(std::make_unique<DUT>()) { dut_->eval(); }
  ~RTLHardware() override = default;

protected:
  std::unique_ptr<DUT> dut_;
};

} // namespace demu::hal
