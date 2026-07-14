#include "demu/sim.hh"
#include "demu/elf_loader.hh"
#include "demu/generated/retire_bindings.hh"
#include "demu/hal/device_registry.hh"
#include "demu/instruction.hh"
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

  dut_ = std::make_unique<soc_t>(context_.get(), "TOP");

  device_manager_ = std::make_unique<demu::hal::DeviceManager>();

  timer_irq_ = std::make_unique<demu::hal::peripheral::InterruptLine>();
  soft_irq_ = std::make_unique<demu::hal::peripheral::InterruptLine>();
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

  hal::register_generated_devices<soc_t>(*device_manager_, dut_.get(),
                                         hal::GeneratedDeviceContext{
                                             .timer_irq = timer_irq_.get(),
                                             .soft_irq = soft_irq_.get(),
                                         });

  device_manager_->dump_device_map();

#ifdef ENABLE_TRACE
  if (trace_enabled_) {
    Verilated::mkdir("logs");
    vcd_ = std::make_unique<VerilatedVcdC>();
    dut_->trace(vcd_.get(), 99);
    vcd_->open(
        ("logs/demu_" + std::string(isa_def::ISA_NAME) + "_trace.vcd").c_str());
    DEMU_DEBUG("VCD tracing enabled: logs/demu_{}_trace.vcd",
               isa_def::ISA_NAME);
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
  _bpu_miss_btb = 0;
  _bpu_miss_direction = 0;
  _bpu_miss_btb_target = 0;
  _bpu_miss_ras_target = 0;
  _bpu_miss_false_hit = 0;
  _bpu_miss_other = 0;
  _flush_cycles = 0;
  _rob_empty_cycles = 0;
  _issue_count = 0;
  _frontend_stalls = 0;
  _backend_stalls = 0;
  _stall_if_redirect = 0;
  _stall_if_ras_wait = 0;
  _stall_ibuffer_full = 0;
  _stall_decode_not_ready = 0;
  _stall_dispatch_not_ready = 0;
  _stall_rob_full = 0;
  _stall_issue_queue_full = 0;
  _stall_lsq_full = 0;
  _stall_flush_recovery = 0;
  _sched_raw_wait = 0;
  _sched_waw_wait = 0;
  _sched_fu_busy = 0;
  _sched_older_lane_block = 0;
  _sched_no_matching_fu = 0;
  _mul_wait = 0;
  _div_wait = 0;
  _load_use_wait = 0;
  _lsu_busy = 0;
  _dcache_wait = 0;
  _store_wait = 0;
  _wb_conflict = 0;
  _rob_head_not_ready = 0;

  terminate_ = false;
  register_values_.fill(0);

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
  while (cycle_count() < target && !is_terminate()) {
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
  DEMU_INFO("    BTB miss:          {:6} ({:.2f} % of misses)",
            _bpu_miss_btb, bpu_miss_share(_bpu_miss_btb) * 100);
  DEMU_INFO("    direction miss:    {:6} ({:.2f} % of misses)",
            _bpu_miss_direction, bpu_miss_share(_bpu_miss_direction) * 100);
  DEMU_INFO("    BTB target miss:   {:6} ({:.2f} % of misses)",
            _bpu_miss_btb_target, bpu_miss_share(_bpu_miss_btb_target) * 100);
  DEMU_INFO("    RAS target miss:   {:6} ({:.2f} % of misses)",
            _bpu_miss_ras_target, bpu_miss_share(_bpu_miss_ras_target) * 100);
  DEMU_INFO("    BTB false hit:     {:6} ({:.2f} % of misses)",
            _bpu_miss_false_hit, bpu_miss_share(_bpu_miss_false_hit) * 100);
  DEMU_INFO("    other:             {:6} ({:.2f} % of misses)",
            _bpu_miss_other, bpu_miss_share(_bpu_miss_other) * 100);
  DEMU_INFO("  Issue Rate:         {:.3f} uOps/cycle", issue_rate());
  DEMU_INFO("  Frontend Starved:   {:.2f} % of cycles (ROB Empty)",
            frontend_starvation_rate() * 100);
  DEMU_INFO("  Frontend Stalled:   {:.2f} % of cycles (Hazards/Full)",
            frontend_stall_rate() * 100);
  DEMU_INFO("    stall_if_redirect:        {:.2f} % of cycles",
            stall_rate(_stall_if_redirect) * 100);
  DEMU_INFO("    stall_if_ras_wait:        {:.2f} % of cycles",
            stall_rate(_stall_if_ras_wait) * 100);
  DEMU_INFO("    stall_ibuffer_full:       {:.2f} % of cycles",
            stall_rate(_stall_ibuffer_full) * 100);
  DEMU_INFO("    stall_decode_not_ready:   {:.2f} % of cycles",
            stall_rate(_stall_decode_not_ready) * 100);
  DEMU_INFO("    stall_dispatch_not_ready: {:.2f} % of cycles",
            stall_rate(_stall_dispatch_not_ready) * 100);
  DEMU_INFO("    stall_rob_full:           {:.2f} % of cycles",
            stall_rate(_stall_rob_full) * 100);
  DEMU_INFO("    stall_issue_queue_full:   {:.2f} % of cycles",
            stall_rate(_stall_issue_queue_full) * 100);
  DEMU_INFO("      sched_raw_wait:         {:.2f} % of cycles",
            stall_rate(_sched_raw_wait) * 100);
  DEMU_INFO("      sched_waw_wait:         {:.2f} % of cycles",
            stall_rate(_sched_waw_wait) * 100);
  DEMU_INFO("      sched_fu_busy:          {:.2f} % of cycles",
            stall_rate(_sched_fu_busy) * 100);
  DEMU_INFO("      sched_older_lane_block: {:.2f} % of cycles",
            stall_rate(_sched_older_lane_block) * 100);
  DEMU_INFO("      sched_no_matching_fu:   {:.2f} % of cycles",
            stall_rate(_sched_no_matching_fu) * 100);
  DEMU_INFO("    stall_lsq_full:           {:.2f} % of cycles",
            stall_rate(_stall_lsq_full) * 100);
  DEMU_INFO("    stall_flush_recovery:     {:.2f} % of cycles",
            stall_rate(_stall_flush_recovery) * 100);
  DEMU_INFO("  Backend Stalled:    {:.2f} % of cycles (Waiting Exe/Mem)",
            backend_stall_rate() * 100);
  DEMU_INFO("    mul_wait:           {:.2f} % of cycles",
            stall_rate(_mul_wait) * 100);
  DEMU_INFO("    div_wait:           {:.2f} % of cycles",
            stall_rate(_div_wait) * 100);
  DEMU_INFO("    load_use_wait:      {:.2f} % of cycles",
            stall_rate(_load_use_wait) * 100);
  DEMU_INFO("    lsu_busy:           {:.2f} % of cycles",
            stall_rate(_lsu_busy) * 100);
  DEMU_INFO("    dcache_wait:        {:.2f} % of cycles",
            stall_rate(_dcache_wait) * 100);
  DEMU_INFO("    store_wait:         {:.2f} % of cycles",
            stall_rate(_store_wait) * 100);
  DEMU_INFO("    wb_conflict:        {:.2f} % of cycles",
            stall_rate(_wb_conflict) * 100);
  DEMU_INFO("    rob_head_not_ready: {:.2f} % of cycles",
            stall_rate(_rob_head_not_ready) * 100);
  DEMU_INFO("")
}

void DemuSimulator::dump_registers() const {
  DEMU_INFO("Register Dump:");
  for (int i = 0; i < isa_def::NUM_ARCH_REGS; i += 4) {
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
  for (uint32_t lane = 0; lane < retire_def::NUM_RETIRE_LANES; ++lane) {
    const retire_def::RetirePacket retire = retire_def::read(dut_.get(), lane);

    if (!retire.valid) {
      continue;
    }

    last_retire_pc_ = retire.pc;

    if (retire.reg_we && retire.reg_addr < isa_def::NUM_ARCH_REGS) {
      register_values_[retire.reg_addr] = retire.reg_data;
      DEMU_REG_WRITE(retire.reg_addr, retire.reg_data);
    }

    Instruction inst(retire.instr);

    if (demu::Logger::demu_should_log(spdlog::level::trace)) {
      DEMU_TRACE("LANE[{}] | Cycle {:6d} | PC=0x{:08x} | Decode={}", lane,
                 cycle_count(), retire.pc,
                 inst.to_string(InstructionLogLevel::Verbose));
    } else if (demu::Logger::demu_should_log(spdlog::level::debug)) {
      DEMU_DEBUG("LANE[{}] | Cycle {:6d} | PC=0x{:08x} | Inst={}", lane,
                 cycle_count(), retire.pc,
                 inst.to_string(InstructionLogLevel::Compact));
    }
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
  _bpu_miss_btb += static_cast<uint64_t>(dut_->debug_bpu_miss_btb);
  _bpu_miss_direction +=
      static_cast<uint64_t>(dut_->debug_bpu_miss_direction);
  _bpu_miss_btb_target +=
      static_cast<uint64_t>(dut_->debug_bpu_miss_btb_target);
  _bpu_miss_ras_target +=
      static_cast<uint64_t>(dut_->debug_bpu_miss_ras_target);
  _bpu_miss_false_hit +=
      static_cast<uint64_t>(dut_->debug_bpu_miss_false_hit);
  _bpu_miss_other += static_cast<uint64_t>(dut_->debug_bpu_miss_other);
  _flush_cycles += static_cast<uint64_t>(dut_->debug_flush_cycle);
  _rob_empty_cycles += static_cast<uint64_t>(dut_->debug_rob_empty);
  _issue_count += static_cast<uint64_t>(dut_->debug_issue_count);
  _frontend_stalls += static_cast<uint64_t>(dut_->debug_frontend_stall);
  _backend_stalls += static_cast<uint64_t>(dut_->debug_backend_stall);
  _stall_if_redirect += static_cast<uint64_t>(dut_->debug_stall_if_redirect);
  _stall_if_ras_wait += static_cast<uint64_t>(dut_->debug_stall_if_ras_wait);
  _stall_ibuffer_full += static_cast<uint64_t>(dut_->debug_stall_ibuffer_full);
  _stall_decode_not_ready +=
      static_cast<uint64_t>(dut_->debug_stall_decode_not_ready);
  _stall_dispatch_not_ready +=
      static_cast<uint64_t>(dut_->debug_stall_dispatch_not_ready);
  _stall_rob_full += static_cast<uint64_t>(dut_->debug_stall_rob_full);
  _stall_issue_queue_full +=
      static_cast<uint64_t>(dut_->debug_stall_issue_queue_full);
  _stall_lsq_full += static_cast<uint64_t>(dut_->debug_stall_lsq_full);
  _stall_flush_recovery +=
      static_cast<uint64_t>(dut_->debug_stall_flush_recovery);
  _sched_raw_wait += static_cast<uint64_t>(dut_->debug_sched_raw_wait);
  _sched_waw_wait += static_cast<uint64_t>(dut_->debug_sched_waw_wait);
  _sched_fu_busy += static_cast<uint64_t>(dut_->debug_sched_fu_busy);
  _sched_older_lane_block +=
      static_cast<uint64_t>(dut_->debug_sched_older_lane_block);
  _sched_no_matching_fu +=
      static_cast<uint64_t>(dut_->debug_sched_no_matching_fu);
  _mul_wait += static_cast<uint64_t>(dut_->debug_mul_wait);
  _div_wait += static_cast<uint64_t>(dut_->debug_div_wait);
  _load_use_wait += static_cast<uint64_t>(dut_->debug_load_use_wait);
  _lsu_busy += static_cast<uint64_t>(dut_->debug_lsu_busy);
  _dcache_wait += static_cast<uint64_t>(dut_->debug_dcache_wait);
  _store_wait += static_cast<uint64_t>(dut_->debug_store_wait);
  _wb_conflict += static_cast<uint64_t>(dut_->debug_wb_conflict);
  _rob_head_not_ready += static_cast<uint64_t>(dut_->debug_rob_head_not_ready);
}

} // namespace demu
