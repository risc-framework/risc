#include "demu/sim.hh"
#include "demu/elf_loader.hh"
#include "demu/logger.hh"

namespace demu {

DemuSimulator::DemuSimulator(bool enabled_trace, int threads, int argc,
                             char **argv)
    : trace_enabled_(enabled_trace) {

  context_ = std::make_unique<VerilatedContext>();
  context_->debug(0);
  context_->randReset(2);
  context_->threads(threads);
  context_->commandArgs(argc, argv);

  if (trace_enabled_) {
    context_->traceEverOn(true);
  }

  dut_ = std::make_unique<system_t>(context_.get(), "TOP");

  device_manager_ = std::make_unique<hal::DeviceManager>();

  timer_irq_ = std::make_unique<demu::hal::InterruptLine>();
  soft_irq_ = std::make_unique<demu::hal::InterruptLine>();
}

DemuSimulator::~DemuSimulator() {
  dut_->final();

#ifdef ENABLE_TRACE
  if (vcd_) {
    vcd_->close();
  }
#endif

#ifdef VM_COVERAGE
  Verilated::mkdir("logs");
  context_->coveragep()->write("logs/coverage.dat");
  DEMU_INFO("Coverage written to logs/coverage.dat");
#endif
}

auto DemuSimulator::load_bin(const std::string &filename, addr_t base_addr)
    -> bool {
  auto *device = device_manager_->find_device_for_address(base_addr);
  if (!device) {
    DEMU_ERROR("No device mapped at address 0x{:08x}", base_addr);
    return false;
  }

  auto *alloc = device->allocator();
  if (!alloc) {
    DEMU_ERROR("Device '{}' has no memory allocator", device->name());
    return false;
  }

  if (alloc->load_binary(filename, base_addr)) {
    return true;
  }

  DEMU_ERROR("Failed to load binary: {}", filename);
  return false;
}

auto DemuSimulator::load_elf(const std::string &filename) -> bool {
  std::vector<ELFSection> sections;
  uint32_t entry_point = 0;

  if (!ELFLoader::load(sections, entry_point, filename)) {
    DEMU_ERROR("Failed to parse ELF: {}", filename);
    return false;
  }

  DEMU_INFO("ELF entry point: 0x{:08x}, {} loadable sections", entry_point,
            sections.size());

  for (const auto &section : sections) {
    if (section.data.empty()) {
      continue;
    }

    auto *device = device_manager_->find_device_for_address(section.addr);
    if (!device) {
      DEMU_ERROR("No device mapped at 0x{:08x} for section '{}'", section.addr,
                 section.name);
      return false;
    }

    auto *alloc = device->allocator();
    if (!alloc) {
      DEMU_ERROR("Device '{}' has no allocator for section '{}'",
                 device->name(), section.name);
      return false;
    }

    for (size_t i = 0; i < section.data.size(); ++i) {
      addr_t addr = section.addr + static_cast<addr_t>(i);
      alloc->write_byte(addr, section.data[i]);
    }

    DEMU_INFO("Loaded section '{}' at 0x{:08x} ({} bytes)", section.name,
              section.addr, section.data.size());
  }

  DEMU_INFO("ELF loaded successfully. Entry: 0x{:08x}", entry_point);
  return true;
}

void DemuSimulator::init() {
  DEMU_INFO("DEMU Simulator Initializing...");

  register_devices();
  device_manager_->dump_device_map();

#ifdef ENABLE_TRACE
  if (trace_enabled_) {
    Verilated::mkdir("logs");
    vcd_ = std::make_unique<VerilatedVcdC>();
    dut_->trace(vcd_.get(), 99);
    vcd_->open(("logs/demu_" + std::string(ISA_NAME) + "_trace.vcd").c_str());
    DEMU_DEBUG("VCD tracing enabled: logs/demu_{}_trace.vcd", ISA_NAME);
  }
#endif
}

void DemuSimulator::reset() {
  DEMU_INFO("Resetting...");
  dut_->reset = 1;
  dut_->clock = 0;
  dut_->eval();
  dut_->clock = 1;
  dut_->eval();
  dut_->reset = 0;
  dut_->eval();

  device_manager_->reset();

  _l1_icache_accesses = 0;
  _l1_icache_misses = 0;
  _l1_dcache_accesses = 0;
  _l1_dcache_misses = 0;

  _bpu_mispredicts = 0;
  _branches_committed = 0;
  _flush_cycles = 0;
  _rob_empty_cycles = 0;
  _issue_count = 0;
  _frontend_stalls = 0;
  _backend_stalls = 0;

  _terminate = false;
  _register_values.fill(0);

  on_reset();
  DEMU_INFO("System Reset Complete. PC: 0x{:08x}",
            static_cast<addr_t>(sys_def::RESET_VECTOR))
}

void DemuSimulator::step(uint64_t cycles) {
  for (uint64_t i = 0; i < cycles; i++) {
    clock_tick();
  }
}

void DemuSimulator::run(uint64_t max_cycles) {
  DEMU_INFO("Starting DEMU Simulation...");
  uint64_t target = max_cycles > 0 ? max_cycles : timeout_;

  auto start_time = std::chrono::high_resolution_clock::now();
  on_init();
  while (cycle_count() < target && !_terminate) {
    clock_tick();
  }
  on_exit();
  auto end_time = std::chrono::high_resolution_clock::now();

  auto duration = std::chrono::duration_cast<std::chrono::microseconds>(
                      end_time - start_time)
                      .count();

  if (cycle_count() >= target) {
    DEMU_WARN("Simulation TIME OUT after {} cycles", cycle_count())
  }

  DEMU_INFO("Simulation completed with: ");
  DEMU_INFO("  {} cycles, {} instructions, IPC: {:.3f} after {:.3f} ms",
            cycle_count(), instret_count(), ipc(), duration / 1000.0);
  DEMU_INFO("  simulation speed: {:.3f} kHz",
            static_cast<float>(cycle_count()) / (duration / 1000.0f))

  DEMU_INFO("")
  DEMU_INFO("--- Memory Performance ---");
  DEMU_INFO("  L1 Icache Hit Rate: {:.2f} % ({} misses / {} accesses)",
            l1_icache_hit_rate() * 100, _l1_icache_misses, _l1_icache_accesses);
  DEMU_INFO("  L1 Dcache Hit Rate: {:.2f} % ({} misses / {} accesses)",
            l1_dcache_hit_rate() * 100, _l1_dcache_misses, _l1_dcache_accesses);

  DEMU_INFO("")
  DEMU_INFO("--- Pipeline Profiling ---");
  DEMU_INFO("  BPU Hit Rate:       {:.2f} % ({} misses / {} branches)",
            bpu_hit_rate() * 100, _bpu_mispredicts, _branches_committed);
  DEMU_INFO("  Issue Rate:         {:.3f} uOps/cycle", issue_rate());
  DEMU_INFO("  Frontend Starved:   {:.2f} % of cycles (ROB Empty)",
            frontend_starvation_rate() * 100);
  DEMU_INFO("  Frontend Stalled:   {:.2f} % of cycles (Hazards/Full)",
            frontend_stall_rate() * 100);
  DEMU_INFO("  Backend Stalled:    {:.2f} % of cycles (Waiting Exe/Mem)",
            backend_stall_rate() * 100);
  DEMU_INFO("")
}

void DemuSimulator::dump_registers() const {
  DEMU_INFO("Register Dump:");
  for (int i = 0; i < NUM_GPRS; i += 4) {
    DEMU_INFO(
        "  x{:02d}={:08x}  x{:02d}={:08x}  x{:02d}={:08x}  x{:02d}={:08x}", i,
        reg(i), i + 1, reg(i + 1), i + 2, reg(i + 2), i + 3, reg(i + 3));
  }
}

void DemuSimulator::dump_memory(addr_t start, size_t size) const {
  const auto *device = device_manager_->find_device_for_address(start);
  if (!device) {
    HAL_WARN("Invalid memory dump address: 0x{:0x8x}", start);
    return;
  }
  device->dump(start, size);
}

void DemuSimulator::clock_tick() {
  DEMU_CPU_TICK(cycle_count());

  context_->timeInc(1);

  dut_->clock = 0;
  device_manager_->handle_ports();
  dut_->eval();

#ifdef ENABLE_TRACE
  if (vcd_) {
    vcd_->dump(context_->time());
  }
#endif

  context_->timeInc(1);
  dut_->clock = 1;
  dut_->eval();

  device_manager_->clock_tick();
  handle_retirements();
  handle_interrupt();
  handle_cache_profiling();
  handle_performance_profiling();

  on_clock_tick();

#ifdef ENABLE_TRACE
  if (vcd_) {
    vcd_->dump(context_->time());
  }
#endif
}

void DemuSimulator::handle_retirements() {
  const uint32_t lanes = active_retire_lanes();

  for (uint32_t lane = 0; lane < lanes; ++lane) {
    const RetirePacket retire = read_retire_lane(lane);

    if (!retire.valid) {
      continue;
    }

    last_retire_pc_ = retire.pc;

    if (retire.reg_we && retire.reg_addr < NUM_GPRS) {
      _register_values[retire.reg_addr] = retire.reg_data;
      DEMU_REG_WRITE(retire.reg_addr, retire.reg_data);
    }

    Instruction inst(retire.instr);
    DEMU_DEBUG("RETIRE[{}] | Cycle {:6d} | PC=0x{:08x} | Inst=0x{:08x} ({})",
               lane, cycle_count(), retire.pc, retire.instr, inst.to_string());
  }
}

void DemuSimulator::handle_interrupt() {
  dut_->irq_timer_irq = timer_irq_->get_level();
  dut_->irq_soft_irq = soft_irq_->get_level();
}

void DemuSimulator::handle_cache_profiling() {
  _l1_icache_accesses += static_cast<uint64_t>(dut_->debug_l1_icache_access);
  _l1_icache_misses += static_cast<uint64_t>(dut_->debug_l1_icache_access &&
                                             dut_->debug_l1_icache_miss);
  _l1_dcache_accesses += static_cast<uint64_t>(dut_->debug_l1_dcache_access);
  _l1_dcache_misses += static_cast<uint64_t>(dut_->debug_l1_dcache_access &&
                                             dut_->debug_l1_dcache_miss);
}

void DemuSimulator::handle_performance_profiling() {
  _bpu_mispredicts += static_cast<uint64_t>(dut_->debug_bpu_mispredict);
  _branches_committed += static_cast<uint64_t>(dut_->debug_branch_commit);
  _flush_cycles += static_cast<uint64_t>(dut_->debug_flush_cycle);
  _rob_empty_cycles += static_cast<uint64_t>(dut_->debug_rob_empty);
  _issue_count += static_cast<uint64_t>(dut_->debug_issue_count);
  _frontend_stalls += static_cast<uint64_t>(dut_->debug_frontend_stall);
  _backend_stalls += static_cast<uint64_t>(dut_->debug_backend_stall);
}

} // namespace demu
