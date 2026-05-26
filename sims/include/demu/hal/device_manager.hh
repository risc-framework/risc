#pragma once

#include "../logger.hh"
#include "./device.hh"
#include "./port_handler.hh"
#include <cstdint>
#include <map>
#include <memory>
#include <optional>
#include <string>
#include <string_view>
#include <unordered_map>
#include <vector>

namespace demu::hal {

using port_id_t = uint8_t;

class DeviceManager final {
public:
  DeviceManager() = default;
  ~DeviceManager() = default;

  // Non-copyable, movable
  DeviceManager(const DeviceManager &) = delete;
  auto operator=(const DeviceManager &) -> DeviceManager & = delete;
  DeviceManager(DeviceManager &&) = default;
  auto operator=(DeviceManager &&) -> DeviceManager & = default;

  // Device Registration
  template <typename T, typename... Args>
  auto register_device(port_id_t port, sys_def::DeviceDescriptor desc,
                       Args &&...args) -> T *;

  // Device Retrieval — by port
  [[nodiscard]] auto get_device(port_id_t port) noexcept -> Device *;
  [[nodiscard]] auto get_device(port_id_t port) const noexcept
      -> const Device *;

  template <typename T>
  [[nodiscard]] auto get_device(port_id_t port) noexcept -> T *;

  template <typename T>
  [[nodiscard]] auto get_device(port_id_t port) const noexcept -> const T *;

  // Device Retrieval — by name
  [[nodiscard]] auto get_device_by_name(std::string_view name) noexcept
      -> Device *;
  [[nodiscard]] auto get_device_by_name(std::string_view name) const noexcept
      -> const Device *;

  template <typename T>
  [[nodiscard]] auto get_device_by_name(std::string_view name) noexcept -> T *;

  template <typename T>
  [[nodiscard]] auto get_device_by_name(std::string_view name) const noexcept
      -> const T *;

  // Device Retrieval — by address
  [[nodiscard]] auto find_device_for_address(addr_t addr) noexcept -> Device *;
  [[nodiscard]] auto find_device_for_address(addr_t addr) const noexcept
      -> const Device *;

  // Port Handlers
  void register_handler(port_id_t port, std::unique_ptr<PortHandler> handler);
  void handle_ports() noexcept;

  // Bulk Operations
  void reset() noexcept;
  void clock_tick() noexcept;

  // Informational
  [[nodiscard]] auto port_count() const noexcept -> size_t {
    return slots_.size();
  }
  [[nodiscard]] auto active_device_count() const noexcept -> size_t {
    return name_indices_.size();
  }
  [[nodiscard]] auto has_device_at(port_id_t port) const noexcept -> bool;
  [[nodiscard]] auto get_device_name(port_id_t port) const noexcept
      -> std::optional<std::string_view>;

  void dump_device_map() const;

private:
  struct DeviceSlot {
    sys_def::DeviceDescriptor desc;
    std::unique_ptr<Device> device;
    std::unique_ptr<PortHandler> handler;
  };

  // components
  std::vector<DeviceSlot> slots_;
  std::unordered_map<std::string, port_id_t> name_indices_;
  std::map<addr_t, port_id_t> addr_indices_;

  // helpers
  void ensure_capacity(port_id_t port);
  void rebuild_indices_for(port_id_t port);
  void remove_indices_for(port_id_t port);
};

template <typename T, typename... Args>
auto DeviceManager::register_device(port_id_t port,
                                    sys_def::DeviceDescriptor desc,
                                    Args &&...args) -> T * {
  static_assert(std::is_base_of_v<Device, T>, "T must derive from Device");

  ensure_capacity(port);

  if (slots_[port].device) {
    remove_indices_for(port);
  }

  try {
    auto device = std::make_unique<T>(desc, std::forward<Args>(args)...);
    T *ptr = device.get();

    slots_[port].desc = desc;
    slots_[port].device = std::move(device);

    rebuild_indices_for(port);

    HAL_DEBUG("Registered device '{}' on Port {} [Base: 0x{:08X}]", desc.name,
              port, static_cast<addr_t>(ptr->base_address()));

    return ptr;
  } catch (const std::exception &e) {
    HAL_ERROR("Failed to create device '{}' on Port {}: {}", desc.name, port,
              e.what());
    return nullptr;
  }
}

template <typename T>
auto DeviceManager::get_device(port_id_t port) noexcept -> T * {
  static_assert(std::is_base_of_v<Device, T>, "T must derive from Device");
  return dynamic_cast<T *>(get_device(port));
}

template <typename T>
auto DeviceManager::get_device(port_id_t port) const noexcept -> const T * {
  static_assert(std::is_base_of_v<Device, T>, "T must derive from Device");
  return dynamic_cast<const T *>(get_device(port));
}

template <typename T>
auto DeviceManager::get_device_by_name(std::string_view name) noexcept -> T * {
  static_assert(std::is_base_of_v<Device, T>, "T must derive from Device");
  return dynamic_cast<T *>(get_device_by_name(name));
}

template <typename T>
auto DeviceManager::get_device_by_name(std::string_view name) const noexcept
    -> const T * {
  static_assert(std::is_base_of_v<Device, T>, "T must derive from Device");
  return dynamic_cast<const T *>(get_device_by_name(name));
}

} // namespace demu::hal
