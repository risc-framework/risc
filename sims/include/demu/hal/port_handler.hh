#pragma once

#include "./hardware.hh"

namespace demu::hal {

class PortHandler {
public:
  virtual ~PortHandler() = default;

  virtual void handle(Hardware *hw) noexcept = 0;

  [[nodiscard]] virtual auto protocol_name() const noexcept -> const char * = 0;
};

} // namespace demu::hal
