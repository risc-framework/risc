#include "debugger.hh"
#include <algorithm>
#include <cctype>
#include <cstdlib>
#include <demu/logger.hh>
#include <fmt/format.h>
#include <readline/history.h>
#include <readline/readline.h>

namespace demu::dbg {

Debugger::Debugger(demu::DemuSimulator &sim) : sim_(sim) {
  register_commands();
}

void Debugger::register_commands() {
  commands_ = {
      {"help", "h", "Show help for all commands or a specific command.",
       "help [command]", [this](auto &a) -> auto { cmd_help(a); }},
      {"run", "r", "Reset and run simulation. Optional max cycle count.",
       "run [max_cycles]", [this](auto &a) -> auto { cmd_run(a); }},
      {"continue", "c", "Continue simulation until breakpoint or N cycles.",
       "continue [cycles]", [this](auto &a) -> auto { cmd_continue(a); }},
      {"step", "s", "Advance N clock cycles (default 1).", "step [n]",
       [this](auto &a) -> auto { cmd_step(a); }},
      {"stepi", "si", "Advance until N instructions retire (default 1).",
       "stepi [n]", [this](auto &a) -> auto { cmd_stepi(a); }},
      {"break", "b", "Set breakpoint at PC address, cycle count, or instret.",
       "break <0xADDR | cyc:N | ret:N>",
       [this](auto &a) -> auto { cmd_break(a); }},
      {"watch", "w", "Set watchpoint on memory address.",
       "watch <0xADDR> [r|w|rw] [size]",
       [this](auto &a) -> auto { cmd_watch(a); }},
      {"delete", "d", "Delete breakpoint or watchpoint by ID.", "delete <id>",
       [this](auto &a) -> auto { cmd_delete(a); }},
      {"enable", "en", "Enable a breakpoint or watchpoint.", "enable <id>",
       [this](auto &a) -> auto { cmd_enable(a); }},
      {"disable", "dis", "Disable a breakpoint or watchpoint.", "disable <id>",
       [this](auto &a) -> auto { cmd_disable(a); }},
      {"info", "i", "Print simulation info.", "info [regs|break|cache|stats]",
       [this](auto &a) -> auto { cmd_info(a); }},
      {"print", "p", "Print a register value.", "print <reg>",
       [this](auto &a) -> auto { cmd_print(a); }},
      {"display", "dp", "Auto-display register at every stop.", "display <reg>",
       [this](auto &a) -> auto { cmd_display(a); }},
      {"undisplay", "ud", "Remove auto-display for register.",
       "undisplay <reg>", [this](auto &a) -> auto { cmd_undisplay(a); }},
      {"memory", "x", "Examine memory.", "memory <0xADDR> [count]",
       [this](auto &a) -> auto { cmd_memory(a); }},
      {"reset", "rst", "Reset the simulator.", "reset",
       [this](auto &a) -> auto { cmd_reset(a); }},
      {"quit", "q", "Exit the debugger.", "quit",
       [this](auto &a) -> auto { cmd_quit(a); }},
  };
}

auto Debugger::find_command(const std::string &input) -> Debugger::Command * {
  for (auto &cmd : commands_) {
    if (cmd.name == input || cmd.alias == input) {
      return &cmd;
    }
  }
  return nullptr;
}

auto Debugger::tokenize(const std::string &line) -> std::vector<std::string> {
  std::vector<std::string> tokens;
  std::string token;
  for (char c : line) {
    if (std::isspace(static_cast<unsigned char>(c))) {
      if (!token.empty()) {
        tokens.push_back(token);
        token.clear();
      }
    } else {
      token += c;
    }
  }
  if (!token.empty()) {
    tokens.push_back(token);
  }
  return tokens;
}

auto Debugger::parse_number(const std::string &s) -> uint64_t {
  if (s.size() > 2 && s[0] == '0' && (s[1] == 'x' || s[1] == 'X')) {
    return std::stoull(s, nullptr, 16);
  }
  return std::stoull(s, nullptr, 10);
}

auto Debugger::parse_reg(const std::string &s) -> int {
  if (s.empty()) {
    return -1;
  }

  std::string lower = s;
  std::transform(lower.begin(), lower.end(), lower.begin(),
                 [](unsigned char c) -> int { return std::tolower(c); });

  std::string num_str = lower;
  if (!num_str.empty() && num_str[0] == 'x') {
    num_str = num_str.substr(1);
  }

  if (num_str.empty()) {
    return -1;
  }

  for (char c : num_str) {
    if (!std::isdigit(static_cast<unsigned char>(c))) {
      return -1;
    }
  }

  try {
    uint64_t val = std::stoul(num_str);
    if (val < isa_def::NUM_ARCH_REGS) {
      return static_cast<int>(val);
    }
  } catch (...) {
  }

  return -1;
}

void Debugger::print_stop_banner() { print_auto_display(); }

void Debugger::print_registers() {
  fmt::print("\n  PC = 0x{:08x}\n\n", sim_.pc());
  for (int i = 0; i < isa_def::NUM_ARCH_REGS; i += 4) {
    fmt::print("  x{:<2d}=0x{:08x}", i, sim_.reg(i));
    if (i + 1 < isa_def::NUM_ARCH_REGS) {
      fmt::print("  x{:<2d}=0x{:08x}", i + 1, sim_.reg(i + 1));
    }
    if (i + 2 < isa_def::NUM_ARCH_REGS) {
      fmt::print("  x{:<2d}=0x{:08x}", i + 2, sim_.reg(i + 2));
    }
    if (i + 3 < isa_def::NUM_ARCH_REGS) {
      fmt::print("  x{:<2d}=0x{:08x}", i + 3, sim_.reg(i + 3));
    }
    fmt::print("\n");
  }
  fmt::print("\n");
}

void Debugger::print_auto_display() {
  for (uint8_t r : auto_display_regs_) {
    fmt::print("  x{} = 0x{:08x}\n", r, sim_.reg(r));
  }
}

void Debugger::repl() {
  fmt::print("\n╔══════════════════════════════════════════╗\n");
  fmt::print("║            DEMU RTL Debugger             ║\n");
  fmt::print("║  Type 'help' for available commands.     ║\n");
  fmt::print("╚══════════════════════════════════════════╝\n\n");

  while (true) {
    std::string prompt = fmt::format("(demu|pc=0x{:08x}|cyc={}) ", sim_.pc(),
                                     sim_.cycle_count());

    char *raw = readline(prompt.c_str());
    if (!raw) {
      fmt::print("\n");
      break;
    }

    std::string line(raw);
    free(raw);

    if (line.empty()) {
      continue;
    }

    add_history(line.c_str());

    auto tokens = tokenize(line);
    if (tokens.empty()) {
      continue;
    }

    Command *cmd = find_command(tokens[0]);
    if (!cmd) {
      fmt::print("Unknown command: '{}'. Type 'help' for commands.\n",
                 tokens[0]);
      continue;
    }

    cmd->handler(tokens);
  }
}

void Debugger::cmd_help(const std::vector<std::string> &args) {
  if (args.size() >= 2) {
    Command *cmd = find_command(args[1]);
    if (cmd) {
      fmt::print("  {} ({}) - {}\n  Usage: {}\n", cmd->name, cmd->alias,
                 cmd->brief, cmd->usage);
      return;
    }
    fmt::print("Unknown command: {}\n", args[1]);
    return;
  }

  fmt::print("\nAvailable commands:\n");
  fmt::print("------------------------------------------------------------\n");
  for (const auto &cmd : commands_) {
    fmt::print("  {:<12s}({:<3s})  {}\n", cmd.name, cmd.alias, cmd.brief);
  }
  fmt::print("\n");
}

void Debugger::cmd_run(const std::vector<std::string> &args) {
  sim_.reset();
  uint64_t max_cycles = 0;
  if (args.size() >= 2) {
    try {
      max_cycles = parse_number(args[1]);
    } catch (...) {
      fmt::print("Invalid cycle count: {}\n", args[1]);
      return;
    }
  }

  running_ = true;
  uint64_t target = max_cycles > 0 ? max_cycles : 1000000;
  uint64_t start = sim_.cycle_count();

  while (sim_.cycle_count() - start < target && running_) {
    sim_.step(1);
    if (bp_mgr_.check_breakpoint(sim_.pc(), sim_.cycle_count(),
                                 sim_.instret_count())) {
      fmt::print("[BREAKPOINT HIT]\n");
      break;
    }
  }

  running_ = false;
  print_stop_banner();
}

void Debugger::cmd_continue(const std::vector<std::string> &args) {
  uint64_t max_cycles = 1000000;
  if (args.size() >= 2) {
    try {
      max_cycles = parse_number(args[1]);
    } catch (...) {
      fmt::print("Invalid cycle count: {}\n", args[1]);
      return;
    }
  }

  running_ = true;
  uint64_t start = sim_.cycle_count();

  while (sim_.cycle_count() - start < max_cycles && running_) {
    sim_.step(1);
    if (bp_mgr_.check_breakpoint(sim_.pc(), sim_.cycle_count(),
                                 sim_.instret_count())) {
      fmt::print("[BREAKPOINT HIT]\n");
      break;
    }
  }

  running_ = false;
  print_stop_banner();
}

void Debugger::cmd_step(const std::vector<std::string> &args) {
  uint64_t n = 1;
  if (args.size() >= 2) {
    try {
      n = parse_number(args[1]);
    } catch (...) {
      fmt::print("Invalid step count: {}\n", args[1]);
      return;
    }
  }

  for (uint64_t i = 0; i < n; i++) {
    sim_.step(1);
    if (bp_mgr_.check_breakpoint(sim_.pc(), sim_.cycle_count(),
                                 sim_.instret_count())) {
      fmt::print("[BREAKPOINT HIT]\n");
      break;
    }
  }

  print_stop_banner();
}

void Debugger::cmd_stepi(const std::vector<std::string> &args) {
  uint64_t n = 1;
  if (args.size() >= 2) {
    try {
      n = parse_number(args[1]);
    } catch (...) {
      fmt::print("Invalid instruction count: {}\n", args[1]);
      return;
    }
  }

  uint64_t start_instret = sim_.instret_count();
  uint64_t safety = sim_.cycle_count() + 1000000;

  while (sim_.instret_count() - start_instret < n &&
         sim_.cycle_count() < safety) {
    sim_.step(1);
    if (bp_mgr_.check_breakpoint(sim_.pc(), sim_.cycle_count(),
                                 sim_.instret_count())) {
      fmt::print("[BREAKPOINT HIT]\n");
      break;
    }
  }

  print_stop_banner();
}

void Debugger::cmd_break(const std::vector<std::string> &args) {
  if (args.size() < 2) {
    fmt::print("Usage: {}\n", find_command("break")->usage);
    return;
  }

  const std::string &arg = args[1];

  if (arg.size() > 4 && arg.substr(0, 4) == "cyc:") {
    try {
      uint64_t val = parse_number(arg.substr(4));
      uint32_t id = bp_mgr_.add_breakpoint(BreakType::CYCLE, val);
      fmt::print("Breakpoint #{} set at cycle {}\n", id, val);
    } catch (...) {
      fmt::print("Invalid cycle value: {}\n", arg.substr(4));
    }
  } else if (arg.size() > 4 && arg.substr(0, 4) == "ret:") {
    try {
      uint64_t val = parse_number(arg.substr(4));
      uint32_t id = bp_mgr_.add_breakpoint(BreakType::INSTRET, val);
      fmt::print("Breakpoint #{} set at instret {}\n", id, val);
    } catch (...) {
      fmt::print("Invalid instret value: {}\n", arg.substr(4));
    }
  } else {
    try {
      uint64_t addr = parse_number(arg);
      uint32_t id = bp_mgr_.add_breakpoint(BreakType::PC, addr);
      fmt::print("Breakpoint #{} set at PC 0x{:08x}\n", id, addr);
    } catch (...) {
      fmt::print("Invalid address: {}\n", arg);
    }
  }
}

void Debugger::cmd_watch(const std::vector<std::string> &args) {
  if (args.size() < 2) {
    fmt::print("Usage: {}\n", find_command("watch")->usage);
    return;
  }

  uint32_t addr;
  try {
    addr = static_cast<uint32_t>(parse_number(args[1]));
  } catch (...) {
    fmt::print("Invalid address: {}\n", args[1]);
    return;
  }

  WatchType wtype = WatchType::WRITE;
  uint32_t size = 4;

  if (args.size() >= 3) {
    if (args[2] == "r") {
      wtype = WatchType::READ;
    } else if (args[2] == "w") {
      wtype = WatchType::WRITE;
    } else if (args[2] == "rw") {
      wtype = WatchType::READWRITE;
    }
  }

  if (args.size() >= 4) {
    try {
      size = static_cast<uint32_t>(parse_number(args[3]));
    } catch (...) {
      fmt::print("Invalid size: {}\n", args[3]);
      return;
    }
  }

  uint32_t id = bp_mgr_.add_watchpoint(addr, size, wtype);
  fmt::print("Watchpoint #{} set at 0x{:08x} size={}\n", id, addr, size);
}

void Debugger::cmd_delete(const std::vector<std::string> &args) {
  if (args.size() < 2) {
    fmt::print("Usage: {}\n", find_command("delete")->usage);
    return;
  }
  try {
    auto id = static_cast<uint32_t>(parse_number(args[1]));
    if (bp_mgr_.remove(id)) {
      fmt::print("Deleted #{}\n", id);
    } else {
      fmt::print("No breakpoint/watchpoint with id #{}\n", id);
    }
  } catch (...) {
    fmt::print("Invalid id: {}\n", args[1]);
  }
}

void Debugger::cmd_enable(const std::vector<std::string> &args) {
  if (args.size() < 2) {
    fmt::print("Usage: {}\n", find_command("enable")->usage);
    return;
  }
  try {
    auto id = static_cast<uint32_t>(parse_number(args[1]));
    if (bp_mgr_.enable(id)) {
      fmt::print("Enabled #{}\n", id);
    } else {
      fmt::print("No breakpoint/watchpoint with id #{}\n", id);
    }
  } catch (...) {
    fmt::print("Invalid id: {}\n", args[1]);
  }
}

void Debugger::cmd_disable(const std::vector<std::string> &args) {
  if (args.size() < 2) {
    fmt::print("Usage: {}\n", find_command("disable")->usage);
    return;
  }
  try {
    auto id = static_cast<uint32_t>(parse_number(args[1]));
    if (bp_mgr_.disable(id)) {
      fmt::print("Disabled #{}\n", id);
    } else {
      fmt::print("No breakpoint/watchpoint with id #{}\n", id);
    }
  } catch (...) {
    fmt::print("Invalid id: {}\n", args[1]);
  }
}

void Debugger::cmd_info(const std::vector<std::string> &args) {
  if (args.size() < 2) {
    print_registers();
    fmt::print("  Cycle   : {}\n", sim_.cycle_count());
    fmt::print("  Instret : {}\n", sim_.instret_count());
    fmt::print("  IPC     : {:.4f}\n\n", sim_.ipc());
    return;
  }

  const std::string &sub = args[1];
  if (sub == "regs" || sub == "r") {
    print_registers();
  } else if (sub == "break" || sub == "b") {
    fmt::print("{}", bp_mgr_.list());
  } else if (sub == "cache" || sub == "c") {
    fmt::print("  L1 ICache hit rate: {:.2f}%\n",
               sim_.l1_icache_hit_rate() * 100.0);
    fmt::print("  L1 DCache hit rate: {:.2f}%\n",
               sim_.l1_dcache_hit_rate() * 100.0);
  } else if (sub == "stats" || sub == "s") {
    fmt::print("  Cycles  : {}\n", sim_.cycle_count());
    fmt::print("  Instret : {}\n", sim_.instret_count());
    fmt::print("  IPC     : {:.4f}\n", sim_.ipc());
    fmt::print("  L1 ICache hit rate: {:.2f}%\n",
               sim_.l1_icache_hit_rate() * 100.0);
    fmt::print("  L1 DCache hit rate: {:.2f}%\n",
               sim_.l1_dcache_hit_rate() * 100.0);
  } else {
    fmt::print("Unknown info subcommand: {}\n", sub);
  }
}

void Debugger::cmd_print(const std::vector<std::string> &args) {
  if (args.size() < 2) {
    fmt::print("Usage: {}\n", find_command("print")->usage);
    return;
  }

  std::string target = args[1];
  std::transform(target.begin(), target.end(), target.begin(),
                 [](unsigned char c) -> int { return std::tolower(c); });

  if (target == "pc") {
    fmt::print("  PC = 0x{:08x}\n", sim_.pc());
    return;
  }

  int r = parse_reg(args[1]);
  if (r < 0) {
    fmt::print("Unknown register: {}\n", args[1]);
    return;
  }

  fmt::print("  x{} = 0x{:08x}\n", r, sim_.reg(r));
}

void Debugger::cmd_display(const std::vector<std::string> &args) {
  if (args.size() < 2) {
    fmt::print("Usage: {}\n", find_command("display")->usage);
    return;
  }

  int r = parse_reg(args[1]);
  if (r < 0) {
    fmt::print("Unknown register: {}\n", args[1]);
    return;
  }

  auto_display_regs_.insert(static_cast<uint8_t>(r));
  fmt::print("Auto-displaying x{}\n", r);
}

void Debugger::cmd_undisplay(const std::vector<std::string> &args) {
  if (args.size() < 2) {
    if (auto_display_regs_.empty()) {
      fmt::print("No registers being auto-displayed.\n");
    } else {
      auto_display_regs_.clear();
      fmt::print("Cleared all auto-display registers.\n");
    }
    return;
  }

  int r = parse_reg(args[1]);
  if (r < 0) {
    fmt::print("Unknown register: {}\n", args[1]);
    return;
  }

  auto_display_regs_.erase(static_cast<uint8_t>(r));
  fmt::print("Removed auto-display for x{}\n", r);
}

void Debugger::cmd_memory(const std::vector<std::string> &args) {
  if (args.size() < 2) {
    fmt::print("Usage: {}\n", find_command("memory")->usage);
    return;
  }

  uint32_t addr;
  try {
    addr = static_cast<uint32_t>(parse_number(args[1]));
  } catch (...) {
    fmt::print("Invalid address: {}\n", args[1]);
    return;
  }

  uint32_t count = 16;
  if (args.size() >= 3) {
    try {
      count = static_cast<uint32_t>(parse_number(args[2]));
    } catch (...) {
      fmt::print("Invalid count: {}\n", args[2]);
      return;
    }
  }

  const auto *device = sim_.device(addr);
  if (!device) {
    fmt::print("No device mapped at 0x{:08x}\n", addr);
    return;
  }

  const auto *alloc = device->allocator();
  if (!alloc) {
    fmt::print("Device '{}' has no memory allocator\n", device->name());
    return;
  }

  uint32_t bytes = count * 4;
  fmt::print("\n  {} [0x{:08x} - 0x{:08x}]\n\n", device->name(), addr,
             addr + bytes - 1);

  for (uint32_t i = 0; i < bytes; i += 16) {
    fmt::print("  0x{:08x}:  ", addr + i);

    for (uint32_t j = 0; j < 16 && (i + j) < bytes; j++) {
      addr_t a = addr + i + j;
      if (alloc->is_valid_addr(a)) {
        fmt::print("{:02x} ", alloc->read_byte(a));
      } else {
        fmt::print("?? ");
      }
      if (j == 7) {
        fmt::print(" ");
      }
    }

    uint32_t remaining = std::min(16u, bytes - i);
    if (remaining < 16) {
      for (uint32_t j = remaining; j < 16; j++) {
        fmt::print("   ");
        if (j == 7) {
          fmt::print(" ");
        }
      }
    }

    fmt::print(" |");
    for (uint32_t j = 0; j < 16 && (i + j) < bytes; j++) {
      addr_t a = addr + i + j;
      if (alloc->is_valid_addr(a)) {
        uint8_t b = alloc->read_byte(a);
        fmt::print("{}", (b >= 0x20 && b < 0x7f) ? static_cast<char>(b) : '.');
      } else {
        fmt::print("?");
      }
    }
    fmt::print("|\n");
  }

  fmt::print("\n");
}

void Debugger::cmd_reset(const std::vector<std::string> &) {
  sim_.reset();
  fmt::print("[DEMU] Reset complete.\n");
}

void Debugger::cmd_quit(const std::vector<std::string> &) {
  fmt::print("Goodbye.\n");
  std::exit(0);
}

} // namespace demu::dbg
