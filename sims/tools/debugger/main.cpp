#include "debugger.hh"
#include <cstdlib>
#include <demu/logger.hh>
#include <demu/sim.hh>
#include <iostream>
#include <string>

class DemuDebuggerTop final : public demu::DemuSimulator {
public:
  explicit DemuDebuggerTop(bool enabled_trace = false,
                           int threads = NUM_THREADS, int argc = 0,
                           char **argv = nullptr)
      : DemuSimulator(enabled_trace, threads, argc, argv) {}

protected:
  void on_init() override {}
  void on_clock_tick() override {}
  void on_exit() override {}
  void on_reset() override {}

private:
};

void print_usage(const char *prog) {
  std::cout << "Usage: " << prog << " [options] <program_file>\n\n";
  std::cout << "Options:\n";
  std::cout << "  -h, --help                    Show this help message\n";
  std::cout << "  -t, --trace                   Enable VCD trace\n";
  std::cout << "  -T, --threads <n>             Number of Verilator threads "
               "(default: NUM_THREADS)\n";
  std::cout
      << "  -b, --base <addr>             Binary load base address (hex)\n";
  std::cout << "  -L12345,                      Set log level (5=error, "
               "4=warn, 3=info, 2=debug, 1=trace)\n";
  std::cout << "  +<arg>                        Native Verilator arguments "
               "(e.g., +verilator+rand+reset+2)\n";
  std::cout << "\nSupported file formats:\n";
  std::cout << "  .bin      Raw binary\n";
  std::cout << "  .elf      ELF executable\n";
  std::cout << std::endl;
}

auto main(int argc, char **argv) -> int {
  std::cout << R"(________  _______   _____ ______   ___  ___     
|\   ___ \|\  ___ \ |\   _ \  _   \|\  \|\  \    
\ \  \_|\ \ \   __/|\ \  \\\__\ \  \ \  \\\  \   
 \ \  \ \\ \ \  \_|/_\ \  \\|__| \  \ \  \\\  \  
  \ \  \_\\ \ \  \_|\ \ \  \    \ \  \ \  \\\  \ 
   \ \_______\ \_______\ \__\    \ \__\ \_______\
    \|_______|\|_______|\|__|     \|__|\|_______|
                                                 
                                                 )"
            << "\n";

  if (argc < 2) {
    print_usage(argv[0]);
    return 1;
  }

  std::string program_file;
  bool enable_trace = false;
  int threads = NUM_THREADS;
  uint32_t base_addr = 0;
  spdlog::level::level_enum spdlog_level = spdlog::level::warn;

  for (int i = 1; i < argc; i++) {
    std::string arg = argv[i];

    if (arg == "-h" || arg == "--help") {
      print_usage(argv[0]);
      return 0;
    } else if (arg == "-t" || arg == "--trace") {
      enable_trace = true;
    } else if (arg == "-T" || arg == "--threads") {
      if (i + 1 < argc) {
        threads = std::stoi(argv[++i]);
      }
    } else if (arg == "-b" || arg == "--base") {
      if (i + 1 < argc) {
        base_addr = std::stoul(argv[++i], nullptr, 16);
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
        std::cerr << "Unknown log level: " << log_level << std::endl;
      }
    } else if (arg[0] == '+') {
      continue;
    } else if (arg[0] != '-') {
      program_file = arg;
    } else {
      std::cerr << "Unknown option: " << arg << std::endl;
      return 1;
    }
  }

  if (program_file.empty()) {
    std::cerr << "Error: No program file specified\n";
    print_usage(argv[0]);
    return 1;
  }

  demu::Logger::init(spdlog_level);

  DemuDebuggerTop sim(enable_trace, threads, argc, argv);

  sim.init();
  sim.reset();

  std::string ext = program_file.substr(program_file.find_last_of('.') + 1);
  bool loaded = false;

  if (ext == "bin") {
    loaded = sim.load_bin(program_file, base_addr);
  } else if (ext == "elf") {
    loaded = sim.load_elf(program_file);
  } else {
    std::cerr << "Error: Unsupported file format: ." << ext << "\n";
    return 1;
  }

  if (!loaded) {
    std::cerr << "Error: Failed to load program\n";
    return 1;
  }

  demu::dbg::Debugger dbg(sim);
  dbg.repl();

  return 0;
}
