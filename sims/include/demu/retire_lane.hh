#pragma once

#include "demu/generated/retire_bindings.hh"

namespace demu {

using RetirePacket = ::demu::retire_def::RetirePacket;

inline constexpr uint32_t NUM_RETIRE_LANES =
    ::demu::retire_def::NUM_RETIRE_LANES;

inline auto read_retire_lane(const isa_def::system_t *dut,
                             uint32_t lane) noexcept -> RetirePacket {
  return ::demu::retire_def::read(dut, lane);
}

} // namespace demu
