#include "demu/generated/sys_def.hh"

#include <cstdint>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <sstream>
#include <stdexcept>
#include <string>
#include <string_view>

namespace {

struct Args {
  std::string template_path;
  std::string output_path;
};

auto usage(const char *argv0) -> void {
  std::cerr << "usage: " << argv0
            << " --template <linker.ld.in> --output <linker.ld>\n";
}

auto parseArgs(int argc, char **argv) -> Args {
  Args args{};

  for (int i = 1; i < argc; ++i) {
    const std::string_view arg = argv[i];

    if (arg == "--template") {
      if (++i >= argc) {
        throw std::runtime_error("--template requires a path");
      }

      args.template_path = argv[i];
    } else if (arg == "--output") {
      if (++i >= argc) {
        throw std::runtime_error("--output requires a path");
      }

      args.output_path = argv[i];
    } else {
      throw std::runtime_error("unknown argument: " + std::string(arg));
    }
  }

  if (args.template_path.empty()) {
    throw std::runtime_error("missing --template");
  }

  if (args.output_path.empty()) {
    throw std::runtime_error("missing --output");
  }

  return args;
}

auto readText(const std::string &path) -> std::string {
  std::ifstream file(path);

  if (!file) {
    throw std::runtime_error("failed to open input file: " + path);
  }

  std::ostringstream ss;
  ss << file.rdbuf();

  return ss.str();
}

auto writeText(const std::string &path, const std::string &text) -> void {
  std::ofstream file(path);

  if (!file) {
    throw std::runtime_error("failed to open output file: " + path);
  }

  file << text;
}

auto replaceAll(std::string text, std::string_view from, std::string_view to)
    -> std::string {
  std::size_t pos = 0;

  while ((pos = text.find(from, pos)) != std::string::npos) {
    text.replace(pos, from.size(), to);
    pos += to.size();
  }

  return text;
}

auto hexU64(std::uint64_t value) -> std::string {
  std::ostringstream ss;
  ss << "0x" << std::hex << std::setw(16) << std::setfill('0') << value;

  return ss.str();
}

auto renderSize(std::uint64_t size) -> std::string {
  if ((size % 1024ull) == 0) {
    return std::to_string(size / 1024ull) + "K";
  }

  return hexU64(size);
}

auto findDevice(std::string_view name) -> demu::sys_def::DeviceDescriptor {
  for (const auto &dev : demu::sys_def::BUS_ADDRESS_MAP) {
    if (dev.name == name) {
      return dev;
    }
  }

  throw std::runtime_error("cannot find device in BUS_ADDRESS_MAP: " +
                           std::string(name));
}

auto checkNonZero(const demu::sys_def::DeviceDescriptor &dev) -> void {
  if (dev.size == 0) {
    throw std::runtime_error("device has zero size: " + std::string(dev.name));
  }
}

auto checkNoOverflow(const demu::sys_def::DeviceDescriptor &dev) -> void {
  if (dev.base + dev.size < dev.base) {
    throw std::runtime_error("device address range overflows: " +
                             std::string(dev.name));
  }
}

auto checkNoOverlap(const demu::sys_def::DeviceDescriptor &a,
                    const demu::sys_def::DeviceDescriptor &b) -> void {
  const auto aBegin = a.base;
  const auto aEnd = a.base + a.size;
  const auto bBegin = b.base;
  const auto bEnd = b.base + b.size;

  if (aBegin < bEnd && bBegin < aEnd) {
    throw std::runtime_error("imem and dmem address ranges overlap");
  }
}

} // namespace

auto main(int argc, char **argv) -> int {
  try {
    const auto args = parseArgs(argc, argv);

    const auto imem = findDevice("imem");
    const auto dmem = findDevice("dmem");

    checkNonZero(imem);
    checkNonZero(dmem);
    checkNoOverflow(imem);
    checkNoOverflow(dmem);
    checkNoOverlap(imem, dmem);

    auto text = readText(args.template_path);

    text = replaceAll(std::move(text), "@IMEM_BASE@", hexU64(imem.base));
    text = replaceAll(std::move(text), "@IMEM_SIZE@", renderSize(imem.size));
    text = replaceAll(std::move(text), "@DMEM_BASE@", hexU64(dmem.base));
    text = replaceAll(std::move(text), "@DMEM_SIZE@", renderSize(dmem.size));

    writeText(args.output_path, text);

    return 0;
  } catch (const std::exception &e) {
    usage(argv[0]);
    std::cerr << "error: " << e.what() << "\n";

    return 1;
  }
}
