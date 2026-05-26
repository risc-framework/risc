#pragma once

#include "./isa/isa.hh"
#include <cstddef>
#include <cstdint>
#include <type_traits>

namespace demu {
using isa_def::addr_t;
using isa_def::instr_t;
using isa_def::word_t;

struct RetirePacket {
  bool valid{false};
  addr_t pc{0};
  instr_t instr{0};
  bool reg_we{false};
  uint8_t reg_addr{0};
  word_t reg_data{0};
};

template <typename DUT, size_t LaneID, typename Enable = void>
struct RetireSignalBinder {
  static constexpr bool exists = false;

  static auto read(const DUT *) noexcept -> RetirePacket { return {}; }
};

#define DEMU_BIND_RETIRE_LANE(LANE_ID)                                         \
  template <typename DUT>                                                      \
  struct RetireSignalBinder<                                                   \
      DUT, LANE_ID,                                                            \
      std::void_t<decltype(std::declval<DUT>().debug_instret_##LANE_ID),       \
                  decltype(std::declval<DUT>().debug_pc_##LANE_ID),            \
                  decltype(std::declval<DUT>().debug_instr_##LANE_ID),         \
                  decltype(std::declval<DUT>().debug_reg_we_##LANE_ID),        \
                  decltype(std::declval<DUT>().debug_reg_addr_##LANE_ID),      \
                  decltype(std::declval<DUT>().debug_reg_data_##LANE_ID)>> {   \
    static constexpr bool exists = true;                                       \
                                                                               \
    static auto read(const DUT *dut) noexcept -> RetirePacket {                \
      RetirePacket packet;                                                     \
      packet.valid = static_cast<bool>(dut->debug_instret_##LANE_ID);          \
      packet.pc = static_cast<addr_t>(dut->debug_pc_##LANE_ID);                \
      packet.instr = static_cast<instr_t>(dut->debug_instr_##LANE_ID);         \
      packet.reg_we = static_cast<bool>(dut->debug_reg_we_##LANE_ID);          \
      packet.reg_addr = static_cast<uint8_t>(dut->debug_reg_addr_##LANE_ID);   \
      packet.reg_data = static_cast<word_t>(dut->debug_reg_data_##LANE_ID);    \
      return packet;                                                           \
    }                                                                          \
  };

DEMU_BIND_RETIRE_LANE(0)
DEMU_BIND_RETIRE_LANE(1)
DEMU_BIND_RETIRE_LANE(2)
DEMU_BIND_RETIRE_LANE(3)
DEMU_BIND_RETIRE_LANE(4)
DEMU_BIND_RETIRE_LANE(5)
DEMU_BIND_RETIRE_LANE(6)
DEMU_BIND_RETIRE_LANE(7)

#undef DEMU_BIND_RETIRE_LANE

template <typename DUT> struct RetireSignalInfo {
  static constexpr uint32_t max_lanes = 8;

  static constexpr uint32_t detected_lanes =
      (RetireSignalBinder<DUT, 7>::exists   ? 8
       : RetireSignalBinder<DUT, 6>::exists ? 7
       : RetireSignalBinder<DUT, 5>::exists ? 6
       : RetireSignalBinder<DUT, 4>::exists ? 5
       : RetireSignalBinder<DUT, 3>::exists ? 4
       : RetireSignalBinder<DUT, 2>::exists ? 3
       : RetireSignalBinder<DUT, 1>::exists ? 2
       : RetireSignalBinder<DUT, 0>::exists ? 1
                                            : 0);

  static auto read(const DUT *dut, uint32_t lane) noexcept -> RetirePacket {
    switch (lane) {
    case 0:
      return RetireSignalBinder<DUT, 0>::read(dut);
    case 1:
      return RetireSignalBinder<DUT, 1>::read(dut);
    case 2:
      return RetireSignalBinder<DUT, 2>::read(dut);
    case 3:
      return RetireSignalBinder<DUT, 3>::read(dut);
    case 4:
      return RetireSignalBinder<DUT, 4>::read(dut);
    case 5:
      return RetireSignalBinder<DUT, 5>::read(dut);
    case 6:
      return RetireSignalBinder<DUT, 6>::read(dut);
    case 7:
      return RetireSignalBinder<DUT, 7>::read(dut);
    default:
      return {};
    }
  }
};

} // namespace demu
