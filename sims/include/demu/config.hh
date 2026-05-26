#pragma once

#include "./isa/isa.hh"
#include "./logger.hh"
#include "risc.pb.h"
#include <filesystem>
#include <fstream>
#include <google/protobuf/util/json_util.h>
#include <sstream>
#include <string>

namespace demu {
using namespace isa;

class RiscConfig {
public:
  RiscConfig() { load(get_config_path()); }
  explicit RiscConfig(const std::string &p) { load(p); }

  [[nodiscard]] auto freq() const noexcept -> const uint64_t {
    return proto_.freq();
  }
  [[nodiscard]] auto ifu() const noexcept -> const risc::IfuConfig & {
    return proto_.ifu();
  }
  [[nodiscard]] auto bpu() const noexcept -> const risc::BpuConfig & {
    return proto_.bpu();
  }
  [[nodiscard]] auto regfile() const noexcept -> const risc::RegfileConfig & {
    return proto_.regfile();
  }
  [[nodiscard]] auto rob() const noexcept -> const risc::RobConfig & {
    return proto_.rob();
  }
  [[nodiscard]] auto l1i() const noexcept -> const risc::CacheConfig & {
    return proto_.l1i();
  }
  [[nodiscard]] auto l1d() const noexcept -> const risc::CacheConfig & {
    return proto_.l1d();
  }
  [[nodiscard]] auto bus() const noexcept -> const risc::BusConfig & {
    return proto_.bus();
  }

  [[nodiscard]] auto is_valid() const noexcept -> bool { return valid_; }

  auto validate() const -> bool {
    bool ok = true;
    if (!find_region("imem")) {
      DEMU_ERROR("RiscConfig: imem not found");
      ok = false;
    }
    if (!find_region("dmem")) {
      DEMU_ERROR("RiscConfig: dmem not found");
      ok = false;
    }
    if (proto_.bus().type() == risc::BUS_TYPE_UNKNOWN) {
      DEMU_ERROR("RiscConfig: unknown BusType");
      ok = false;
    }
    return ok;
  }

  void dump() const {
    DEMU_DEBUG("--- RiscConfig ---");
    DEMU_DEBUG("  config: {}", config_path_);
    std::string json;
    (void)google::protobuf::util::MessageToJsonString(proto_, &json);
    std::istringstream ss(json);
    std::string line;
    while (std::getline(ss, line))
      DEMU_TRACE("  {}", line);
    for (const auto &r : proto_.bus().address_map())
      DEMU_DEBUG("    {:6s} base=0x{:08x} size=0x{:x}", r.name(), r.base(),
                 r.size());
    DEMU_DEBUG("------------------")
  }

  [[nodiscard]] auto find_region(const std::string &dev) const noexcept
      -> const risc::DeviceDescriptor * {
    for (const auto &r : proto_.bus().address_map()) {
      if (r.name() == dev) {
        return &r;
      }
    }
    return nullptr;
  }

private:
  risc::RiscConfig proto_;
  std::string config_path_;
  bool valid_{false};

  static auto get_config_path() -> std::string {
#ifdef RTL_CONFIG_FILE
    return {RTL_CONFIG_FILE};
#else
    DEMU_ERROR("RTL_CONFIG_FILE not defined");
    return "";
#endif
  }

  void load(const std::string &path) {
    config_path_ = path;
    if (!std::filesystem::exists(path)) {
      DEMU_ERROR("Config not found: {}", path);
      return;
    }

    if (path.size() >= 3 && path.substr(path.size() - 3) == ".pb") {
      std::ifstream f(path, std::ios::binary);
      if (!proto_.ParseFromIstream(&f)) {
        DEMU_ERROR("Failed to parse binary protobuf: {}", path);
        return;
      }
    } else {
      std::ifstream f(path);
      std::string json((std::istreambuf_iterator<char>(f)),
                       std::istreambuf_iterator<char>());
      auto status = google::protobuf::util::JsonStringToMessage(json, &proto_);
      if (!status.ok()) {
        DEMU_ERROR("Failed to parse JSON protobuf: {}", status.ToString());
        return;
      }
    }

    valid_ = true;
    DEMU_INFO("Config loaded: {}", path);
  }
};

} // namespace demu
