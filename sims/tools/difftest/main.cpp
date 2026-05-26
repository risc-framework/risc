#include "demu/elf_loader.hh"
#include "gdb_ref_model.hh"
#include "ref_model.hh"
#include <atomic>
#include <condition_variable>
#include <cstdlib>
#include <cstring>
#include <demu.hh>
#include <fstream>
#include <iostream>
#include <mutex>
#include <queue>
#include <thread>
#include <vector>

using demu::isa_def::addr_t;
using demu::isa_def::instr_t;
using demu::isa_def::word_t;

struct CommitState {
  addr_t pc;
  uint64_t cycle;
  bool reg_we;
  uint8_t reg_addr;
  word_t reg_data;
};

class DemuSimulatorDiff final : public demu::DemuSimulator {
public:
  explicit DemuSimulatorDiff(
      std::unique_ptr<demu::difftest::IRefModel> ref_model,
      bool enabled_trace = false, int threads = NUM_THREADS,
      size_t batch_size = 1024, size_t max_queue_batches = 10, int argc = 0,
      char **argv = nullptr)
      : DemuSimulator(enabled_trace, threads, argc, argv),
        ref_model_(std::move(ref_model)),
        batch_size_(batch_size > 0 ? batch_size : 1),
        max_queue_batches_(max_queue_batches > 0 ? max_queue_batches : 1),
        safe_loop_counter_(0) {}

  auto load_bin(const std::string &filename, addr_t base_addr = 0) -> bool {
    entry_point_ = base_addr;
    bool ok = DemuSimulator::load_bin(filename, base_addr);
    if (ok) {
      std::ifstream file(filename, std::ios::binary);
      std::vector<uint8_t> buf(std::istreambuf_iterator<char>(file), {});
      ref_model_->sync_memory(base_addr, buf.size(), buf.data());
    }
    return ok;
  }

  auto load_elf(const std::string &filename) -> bool {
    bool ok = DemuSimulator::load_elf(filename);
    if (ok) {
      std::vector<demu::ELFSection> sections;
      demu::ELFLoader::load(sections, entry_point_, filename);
      for (const auto &sec : sections) {
        if (!sec.data.empty()) {
          ref_model_->sync_memory(sec.addr, sec.data.size(), sec.data.data());
        }
      }
      DEMU_INFO("Difftest: Synced ELF memory. Captured Entry PC: 0x{:08x}",
                entry_point_);
    }
    return ok;
  }

  void sync_ref_state() {
    ref_model_->set_pc(entry_point_);
    for (int i = 0; i < demu::isa_def::NUM_ARCH_REGS; i++) {
      ref_model_->set_reg(i, 0);
    }
    ref_model_->push_state();
    DEMU_INFO("Difftest: Synchronized Initial State to REF Model (PC=0x{:08x})",
              entry_point_);
  }

protected:
  struct RetirePacket {
    bool valid{false};
    addr_t pc{0};
    instr_t instr{0};
    bool reg_we{false};
    uint8_t reg_addr{0};
    word_t reg_data{0};
  };

  auto read_retire_lane0() const noexcept -> RetirePacket {
    return RetirePacket{
        .valid = static_cast<bool>(dut_->debug_instret_0),
        .pc = static_cast<addr_t>(dut_->debug_pc_0),
        .instr = static_cast<instr_t>(dut_->debug_instr_0),
        .reg_we = static_cast<bool>(dut_->debug_reg_we_0),
        .reg_addr = static_cast<uint8_t>(dut_->debug_reg_addr_0),
        .reg_data = static_cast<word_t>(dut_->debug_reg_data_0),
    };
  }

  auto read_retire_lane1() const noexcept -> RetirePacket {
    return RetirePacket{
        .valid = static_cast<bool>(dut_->debug_instret_1),
        .pc = static_cast<addr_t>(dut_->debug_pc_1),
        .instr = static_cast<instr_t>(dut_->debug_instr_1),
        .reg_we = static_cast<bool>(dut_->debug_reg_we_1),
        .reg_addr = static_cast<uint8_t>(dut_->debug_reg_addr_1),
        .reg_data = static_cast<word_t>(dut_->debug_reg_data_1),
    };
  }

  void register_devices() override {
    register_port<0, demu::hal::axif::AXIFullPortHandler,
                  demu::hal::axif::AXIFullSRAM>("imem");
    register_port<1, demu::hal::axif::AXIFullPortHandler,
                  demu::hal::axif::AXIFullSRAM>("dmem");
    register_port<2, demu::hal::axif::AXIFullPortHandler,
                  demu::hal::axif::AXIFullUART>("uart");

#if defined(__ISA_RV32I__) || defined(__ISA_RV32IM__)
    register_port<3, demu::hal::axif::AXIFullPortHandler,
                  demu::hal::axif::AXIFullCLINT>(
        "clint", demu::sys_def::FREQ, timer_irq_.get(), soft_irq_.get());
#endif
  };

  void on_init() override {
    difftest_error_.store(false);
    sim_running_.store(true);
    local_batch_.clear();
    local_batch_.reserve(batch_size_);

    difftest_thread_ = std::thread(&DemuSimulatorDiff::difftest_worker, this);
  }

  void on_exit() override {
    {
      std::unique_lock<std::mutex> lock(mtx_);
      if (!local_batch_.empty()) {
        state_queue_.push(std::move(local_batch_));
        local_batch_.clear();
      }
      sim_running_.store(false);
    }
    cv_consume_.notify_one();

    if (difftest_thread_.joinable()) {
      difftest_thread_.join();
    }
  }

  void on_reset() override {}

  void on_clock_tick() override {
    if (__builtin_expect(difftest_error_.load(std::memory_order_relaxed), 0)) {
      _terminate = true;
      return;
    }

    const uint32_t lanes = active_retire_lanes();

    for (uint32_t lane = 0; lane < lanes; ++lane) {
      const demu::RetirePacket retire = read_retire_lane(lane);

      if (!retire.valid) {
        continue;
      }

      CommitState state;
      state.pc = retire.pc;
      state.cycle = cycle_count();
      state.reg_we = retire.reg_we;
      state.reg_addr = retire.reg_addr;
      state.reg_data = retire.reg_data;

      local_batch_.push_back(state);

      if (__builtin_expect(local_batch_.size() >= batch_size_, 0)) {
        std::unique_lock<std::mutex> lock(mtx_);

        cv_produce_.wait(lock, [this]() {
          return state_queue_.size() < max_queue_batches_ ||
                 difftest_error_.load(std::memory_order_relaxed);
        });

        if (__builtin_expect(difftest_error_.load(std::memory_order_relaxed),
                             0)) {
          _terminate = true;
          return;
        }

        state_queue_.push(std::move(local_batch_));
        local_batch_.clear();
        local_batch_.reserve(batch_size_);

        lock.unlock();
        cv_consume_.notify_one();
      }
    }
  }

private:
  uint32_t entry_point_ = demu::sys_def::RESET_VECTOR;
  std::unique_ptr<demu::difftest::IRefModel> ref_model_;

  std::thread difftest_thread_;
  std::mutex mtx_;
  std::condition_variable cv_produce_;
  std::condition_variable cv_consume_;
  std::queue<std::vector<CommitState>> state_queue_;
  std::vector<CommitState> local_batch_;
  size_t batch_size_;
  size_t max_queue_batches_;
  size_t safe_loop_counter_;

  std::atomic<bool> sim_running_{false};
  std::atomic<bool> difftest_error_{false};

  void difftest_worker() {
    addr_t expected_qemu_pc = entry_point_;

    while (true) {
      std::vector<CommitState> batch;

      {
        std::unique_lock<std::mutex> lock(mtx_);
        cv_consume_.wait(lock, [this]() {
          return !state_queue_.empty() || !sim_running_.load();
        });

        if (state_queue_.empty() && !sim_running_.load()) {
          break;
        }

        batch = std::move(state_queue_.front());
        state_queue_.pop();
      }
      cv_produce_.notify_one();

      for (const auto &dut_state : batch) {
        if (expected_qemu_pc != dut_state.pc) {
          DEMU_ERROR("Difftest PC Mismatch at Cycle {}! | DUT: 0x{:08x} | REF: "
                     "0x{:08x}",
                     dut_state.cycle, dut_state.pc, expected_qemu_pc);
          difftest_error_.store(true, std::memory_order_relaxed);
          return;
        }

        ref_model_->step(1);
        ref_model_->pull_state();

        expected_qemu_pc = ref_model_->get_pc();

        if (dut_state.reg_we &&
            dut_state.reg_addr < demu::isa_def::NUM_ARCH_REGS) {
          word_t ref_val = ref_model_->get_reg(dut_state.reg_addr);
          word_t dut_val = dut_state.reg_data;

          if (ref_val != dut_val) {
            DEMU_ERROR("Difftest GPR[x{:02d}] Mismatch at Cycle {}! | DUT: "
                       "0x{:08x} | REF: 0x{:08x}",
                       dut_state.reg_addr, dut_state.cycle, dut_val, ref_val);
            difftest_error_.store(true, std::memory_order_relaxed);
            return;
          }
        }
      }
    }
  }
};

void print_usage(const char *prog) {
  std::cout << R"(________  _______   _____ ______   ___  ___     
|\   ___ \|\  ___ \ |\   _ \  _   \|\  \|\  \    
\ \  \_|\ \ \   __/|\ \  \\\__\ \  \ \  \\\  \   
 \ \  \ \\ \ \  \_|/_\ \  \\|__| \  \ \  \\\  \  
  \ \  \_\\ \ \  \_|\ \ \  \    \ \  \ \  \\\  \ 
   \ \_______\ \_______\ \__\    \ \__\ \_______\
    \|_______|\|_______|\|__|     \|__|\|_______|
                                                 
                                                 )"
            << "\n";

  std::cout << "Usage: " << prog << " [options] <program_file>\n\n";
  std::cout << "Options:\n";
  std::cout << "  -h, --help                    Show this help message\n";
  std::cout << "  -R, --ref-so <path|gdb>       Path to Reference Model .so OR "
               "type 'gdb' for QEMU TCP\n";
  std::cout
      << "  -B --batch-size <n>                 Difftest batch size (default: "
         "1024)\n";
  std::cout
      << "  -Q --max-batches <n>                Max batches in async queue "
         "(default: 10)\n";
  std::cout << "  -t, --trace                   Enable VCD trace\n";
  std::cout << "  -T, --threads <n>             Number of Verilator threads "
               "(default: NUM_THREADS)\n";
  std::cout << "  -SLT, --safe-loop-terminate               Safe return when "
               "stucking at infinite loop (default: false)\n";
  std::cout
      << "  -c, --cycles <n>              Run for n cycles (0=unlimited)\n";
  std::cout
      << "  -d, --dump-regs               Dump registers after execution\n";
  std::cout << "  -m, --dump-mem <addr> <size>  Dump memory region\n";
  std::cout << "  -L12345,                      Set log level (5=err, 4=warn, "
               "3=info, 2=debug, 1=trace)\n";
}

auto main(int argc, char **argv) -> int {
  if (argc < 2) {
    print_usage(argv[0]);
    return 1;
  }

  std::string program_file;
  std::string ref_so_path;
  bool enable_trace = false;
  bool dump_regs = false;
  bool dump_mem = false;
  int threads = NUM_THREADS;
  uint64_t max_cycles = 0;
  uint32_t base_addr = 0;
  uint32_t dump_mem_addr = 0;
  uint32_t dump_mem_size = 0;
  size_t batch_size = 1024;
  size_t max_batches = 10;
  spdlog::level::level_enum spdlog_level = spdlog::level::info;

  for (int i = 1; i < argc; i++) {
    std::string arg = argv[i];

    if (arg == "-h" || arg == "--help") {
      print_usage(argv[0]);
      return 0;
    } else if (arg == "-R" || arg == "--ref-so") {
      if (i + 1 < argc) {
        ref_so_path = argv[++i];
      }
    } else if (arg == "-B" || arg == "--batch-size") {
      if (i + 1 < argc) {
        batch_size = std::stoull(argv[++i]);
      }
    } else if (arg == "-Q" || arg == "--max-batches") {
      if (i + 1 < argc) {
        max_batches = std::stoull(argv[++i]);
      }
    } else if (arg == "-t" || arg == "--trace") {
      enable_trace = true;
    } else if (arg == "-T" || arg == "--threads") {
      if (i + 1 < argc) {
        threads = std::stoi(argv[++i]);
      }
    } else if (arg == "-d" || arg == "--dump-regs") {
      dump_regs = true;
    } else if (arg == "-c" || arg == "--cycles") {
      if (i + 1 < argc) {
        max_cycles = std::stoull(argv[++i]);
      }
    } else if (arg == "-b" || arg == "--base") {
      if (i + 1 < argc) {
        base_addr = std::stoul(argv[++i], nullptr, 16);
      }
    } else if (arg == "-m" || arg == "--dump-mem") {
      if (i + 2 < argc) {
        dump_mem_addr = std::stoul(argv[++i], nullptr, 16);
        dump_mem_size = std::stoul(argv[++i], nullptr, 16);
        dump_mem = true;
      }
    } else if (arg[0] == '-' && arg.length() > 1 && arg[1] == 'L') {
      int log_level = std::stoi(arg.substr(2));
      switch (log_level) {
      case 1:
        spdlog_level = spdlog::level::trace;
        break;
      case 2:
        spdlog_level = spdlog::level::debug;
        break;
      case 3:
        spdlog_level = spdlog::level::info;
        break;
      case 4:
        spdlog_level = spdlog::level::warn;
        break;
      case 5:
        spdlog_level = spdlog::level::err;
        break;
      default:
        break;
      }
    } else if (arg[0] == '+') {
      continue;
    } else if (arg[0] != '-') {
      program_file = arg;
    }
  }

  demu::Logger::init(spdlog_level);

  if (program_file.empty()) {
    std::cerr << "Error: No program file specified\n";
    return 1;
  }

#ifdef REF_SO_PATH
  if (ref_so_path.empty()) {
    ref_so_path = REF_SO_PATH;
  }
#endif

  if (ref_so_path.empty()) {
    std::cerr << "Error: You must specify the reference model SO path using "
                 "--ref-so <path>\n";
    return 1;
  }

  auto ref = demu::difftest::create_ref_model(ref_so_path);
  if (!ref || !ref->init()) {
    std::cerr << "Error: Failed to initialize Reference Model.\n";
    return 1;
  }

  DemuSimulatorDiff sim(std::move(ref), enable_trace, threads, batch_size,
                        max_batches, argc, argv);

  sim.init();
  sim.reset();

  bool loaded = false;
  if (program_file.find(".bin") != std::string::npos) {
    loaded = sim.load_bin(program_file, base_addr);
  } else if (program_file.find(".elf") != std::string::npos) {
    loaded = sim.load_elf(program_file);
  } else {
    std::cerr << "Error: Unsupported file format\n";
    return 1;
  }

  if (!loaded) {
    std::cerr << "Error: Failed to load program\n";
    return 1;
  }

  sim.sync_ref_state();
  sim.run(max_cycles);

  if (dump_regs) {
    sim.dump_registers();
  }
  if (dump_mem) {
    sim.dump_memory(dump_mem_addr, dump_mem_size);
  }

  return 0;
}
