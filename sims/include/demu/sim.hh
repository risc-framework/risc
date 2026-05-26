#pragma once

#include "./hal/hal.hh"
#include "./retire_lane.hh"
#include "verilated.h"
#include <cstdint>
#include <memory>
#include <string>

#ifdef ENABLE_TRACE
#include "verilated_vcd_c.h"
#endif

namespace demu {
using namespace isa;

class DemuSimulator {
public:
  explicit DemuSimulator(bool enabled_trace = false, int threads = NUM_THREADS,
                         int argc = 0, char **argv = nullptr);
  ~DemuSimulator();

  // Program loading
  auto load_bin(const std::string &filename, addr_t offset = 0) -> bool;
  auto load_elf(const std::string &filename) -> bool;

  // Simulation control
  void init();
  void reset();
  void step(uint64_t cycles = 1);
  void run(uint64_t max_cycles = 0);

  // Architecture state access
  [[nodiscard]] auto device(addr_t addr) -> hal::Device * {
    return device_manager_->find_device_for_address(addr);
  }
  [[nodiscard]] auto pc() const noexcept -> addr_t { return last_retire_pc_; }
  [[nodiscard]] auto reg(uint8_t reg) const noexcept -> word_t {
    return _register_values[reg];
  }

  // Simulator configuration
  void timeout(uint64_t timeout) noexcept { timeout_ = timeout; }

  // Simulator statistics
  [[nodiscard]] auto cycle_count() const noexcept -> uint64_t {
    return dut_->debug_cycle_count;
  }
  [[nodiscard]] auto instret_count() const -> uint64_t {
    return dut_->debug_instret_count;
  }
  [[nodiscard]] auto ipc() const noexcept -> double {
    return dut_->debug_cycle_count > 0
               ? static_cast<double>(dut_->debug_instret_count) /
                     dut_->debug_cycle_count
               : 0.0;
  };
  [[nodiscard]] auto l1_icache_hit_rate() const noexcept -> double {
    return _l1_icache_accesses > 0
               ? 1.0 - static_cast<double>(_l1_icache_misses) /
                           _l1_icache_accesses
               : 0.0;
  };
  [[nodiscard]] auto l1_dcache_hit_rate() const noexcept -> double {
    return _l1_dcache_accesses > 0
               ? 1.0 - static_cast<double>(_l1_dcache_misses) /
                           _l1_dcache_accesses
               : 0.0;
  };
  [[nodiscard]] auto bpu_hit_rate() const noexcept -> double {
    return _branches_committed > 0
               ? 1.0 -
                     static_cast<double>(_bpu_mispredicts) / _branches_committed
               : 0.0;
  }
  [[nodiscard]] auto issue_rate() const noexcept -> double {
    return dut_->debug_cycle_count > 0
               ? static_cast<double>(_issue_count) / dut_->debug_cycle_count
               : 0.0;
  }
  [[nodiscard]] auto frontend_starvation_rate() const noexcept -> double {
    return dut_->debug_cycle_count > 0
               ? static_cast<double>(_rob_empty_cycles) /
                     dut_->debug_cycle_count
               : 0.0;
  }
  [[nodiscard]] auto frontend_stall_rate() const noexcept -> double {
    return dut_->debug_cycle_count > 0
               ? static_cast<double>(_frontend_stalls) / dut_->debug_cycle_count
               : 0.0;
  }
  [[nodiscard]] auto backend_stall_rate() const noexcept -> double {
    return dut_->debug_cycle_count > 0
               ? static_cast<double>(_backend_stalls) / dut_->debug_cycle_count
               : 0.0;
  }

  // Debug output
  void dump_registers() const;
  void dump_memory(addr_t start, size_t size) const;

protected:
  // components
  std::unique_ptr<VerilatedContext> context_;
  std::unique_ptr<system_t> dut_;
  std::unique_ptr<hal::DeviceManager> device_manager_;

  std::unique_ptr<demu::hal::InterruptLine> timer_irq_;
  std::unique_ptr<demu::hal::InterruptLine> soft_irq_;

#ifdef ENABLE_TRACE
  std::unique_ptr<VerilatedVcdC> vcd_;
#endif

  uint64_t timeout_{1000000};
  bool trace_enabled_{false};

  // Simulator state
  bool _terminate{false};

  // Cache Profiling
  uint64_t _l1_icache_accesses{0};
  uint64_t _l1_icache_misses{0};
  uint64_t _l1_dcache_accesses{0};
  uint64_t _l1_dcache_misses{0};

  // Advanced Pipeline Profiling
  uint64_t _bpu_mispredicts{0};
  uint64_t _branches_committed{0};
  uint64_t _flush_cycles{0};
  uint64_t _rob_empty_cycles{0};
  uint64_t _issue_count{0};
  uint64_t _frontend_stalls{0};
  uint64_t _backend_stalls{0};

  addr_t last_retire_pc_{0};
  std::array<word_t, NUM_GPRS> _register_values{};

  // Internal simulation methods
  void clock_tick();
  void handle_retirements();
  void handle_interrupt();
  void handle_cache_profiling();
  void handle_performance_profiling();

  // Overridable hooks
  virtual void register_devices() {};
  virtual void on_clock_tick() {};
  virtual void on_init() {};
  virtual void on_exit() {};
  virtual void on_reset() {};

  // retire lane helper
  [[nodiscard]] auto active_retire_lanes() const noexcept -> uint32_t {
    const uint32_t config_lanes =
        static_cast<uint32_t>(config_->ifu().issue_width());

    constexpr uint32_t detected_lanes =
        demu::RetireSignalInfo<system_t>::detected_lanes;

    if (config_lanes == 0 || config_lanes > detected_lanes) {
      return detected_lanes;
    }

    return config_lanes;
  }

  [[nodiscard]] auto read_retire_lane(uint32_t lane) const noexcept
      -> demu::RetirePacket {
    return demu::RetireSignalInfo<system_t>::read(dut_.get(), lane);
  }

  // device registry helper
  template <size_t PortID, typename HandlerType, typename DeviceType,
            typename... Args>
  auto register_port(const std::string &region_name, Args &&...args) -> void {

    auto *specific_dut = static_cast<system_t *>(this->dut_.get());

    if constexpr (demu::hal::SignalBinder<system_t, HandlerType,
                                          PortID>::exists) {

      const auto *region = config_->find_region(region_name);
      if (!region) {
        DEMU_WARN("Region '{}' for Port {} not found in config. Skipping.",
                  region_name, PortID);
        return;
      }

      device_manager_->register_device<DeviceType>(PortID, *region,
                                                   std::forward<Args>(args)...);

      device_manager_->register_handler(
          PortID, std::make_unique<HandlerType>([specific_dut]() -> auto {
            return demu::hal::SignalBinder<system_t, HandlerType, PortID>::bind(
                specific_dut);
          }));

      DEMU_DEBUG("Registered '{}' on Port {}", region_name, PortID)

    } else {
      DEMU_ERROR(
          "Compile-Time SFINAE Failed: Port {} does not exist on DUT for '{}'",
          PortID, region_name);
    }
  }
};
} // namespace demu
