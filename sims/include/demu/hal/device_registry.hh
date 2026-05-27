#pragma once

#include "demu/generated/bus_bindings.hh"
#include "demu/generated/sys_def.hh"
#include "demu/hal/bus/axif/interrupt.hh"
#include "demu/hal/bus/axif/port_handler.hh"
#include "demu/hal/bus/axif/sram.hh"
#include "demu/hal/bus/axif/uart.hh"
#include "demu/hal/bus/axil/interrupt.hh"
#include "demu/hal/bus/axil/port_handler.hh"
#include "demu/hal/bus/axil/sram.hh"
#include "demu/hal/bus/axil/uart.hh"
#include "demu/hal/device_manager.hh"
#include "demu/hal/interrupt.hh"
#include "demu/logger.hh"
#include <cstddef>
#include <memory>
#include <type_traits>
#include <utility>

namespace demu::hal {

struct GeneratedDeviceContext {
  InterruptLine *timer_irq{nullptr};
  InterruptLine *soft_irq{nullptr};
};

template <typename T> inline constexpr bool always_false_v = false;

template <sys_def::BusType Bus, sys_def::DeviceType DeviceKind>
struct DeviceBinding {
  static constexpr bool supported = false;
};

// AXI4-Full bindings
template <>
struct DeviceBinding<sys_def::BusType::BUS_TYPE_AXIF,
                     sys_def::DeviceType::DEVICE_TYPE_SRAM> {
  static constexpr bool supported = true;

  using Handler = axif::AXIFullPortHandler;
  using Device = axif::AXIFullSRAM;

  template <size_t PortID>
  static auto make_handler(isa_def::system_t *dut) -> std::unique_ptr<Handler> {
    return std::make_unique<Handler>(
        bus_def::AXIFPortBinding<PortID>::bind(dut));
  }

  static auto create(DeviceManager &manager, port_id_t port,
                     const sys_def::DeviceDescriptor &desc,
                     const GeneratedDeviceContext &) -> Device * {
    return manager.register_device<Device>(port, desc);
  }
};

template <>
struct DeviceBinding<sys_def::BusType::BUS_TYPE_AXIF,
                     sys_def::DeviceType::DEVICE_TYPE_UART> {
  static constexpr bool supported = true;

  using Handler = axif::AXIFullPortHandler;
  using Device = axif::AXIFullUART;

  template <size_t PortID>
  static auto make_handler(isa_def::system_t *dut) -> std::unique_ptr<Handler> {
    return std::make_unique<Handler>(
        bus_def::AXIFPortBinding<PortID>::bind(dut));
  }

  static auto create(DeviceManager &manager, port_id_t port,
                     const sys_def::DeviceDescriptor &desc,
                     const GeneratedDeviceContext &) -> Device * {
    return manager.register_device<Device>(port, desc);
  }
};

template <>
struct DeviceBinding<sys_def::BusType::BUS_TYPE_AXIF,
                     sys_def::DeviceType::DEVICE_TYPE_IRH> {
  static constexpr bool supported = true;

  using Handler = axif::AXIFullPortHandler;
  using Device = axif::AXIFullCLINT;

  template <size_t PortID>
  static auto make_handler(isa_def::system_t *dut) -> std::unique_ptr<Handler> {
    return std::make_unique<Handler>(
        bus_def::AXIFPortBinding<PortID>::bind(dut));
  }

  static auto create(DeviceManager &manager, port_id_t port,
                     const sys_def::DeviceDescriptor &desc,
                     const GeneratedDeviceContext &ctx) -> Device * {
    return manager.register_device<Device>(port, desc, sys_def::FREQ,
                                           ctx.timer_irq, ctx.soft_irq);
  }
};

// AXI4-Lite bindings
template <>
struct DeviceBinding<sys_def::BusType::BUS_TYPE_AXIL,
                     sys_def::DeviceType::DEVICE_TYPE_SRAM> {
  static constexpr bool supported = true;

  using Handler = axil::AXILitePortHandler;
  using Device = axil::AXILiteSRAM;

  template <size_t PortID>
  static auto make_handler(isa_def::system_t *dut) -> std::unique_ptr<Handler> {
    return std::make_unique<Handler>(
        bus_def::AXILPortBinding<PortID>::bind(dut));
  }

  static auto create(DeviceManager &manager, port_id_t port,
                     const sys_def::DeviceDescriptor &desc,
                     const GeneratedDeviceContext &) -> Device * {
    return manager.register_device<Device>(port, desc);
  }
};

template <>
struct DeviceBinding<sys_def::BusType::BUS_TYPE_AXIL,
                     sys_def::DeviceType::DEVICE_TYPE_UART> {
  static constexpr bool supported = true;

  using Handler = axil::AXILitePortHandler;
  using Device = axil::AXILiteUART;

  template <size_t PortID>
  static auto make_handler(isa_def::system_t *dut) -> std::unique_ptr<Handler> {
    return std::make_unique<Handler>(
        bus_def::AXILPortBinding<PortID>::bind(dut));
  }

  static auto create(DeviceManager &manager, port_id_t port,
                     const sys_def::DeviceDescriptor &desc,
                     const GeneratedDeviceContext &) -> Device * {
    return manager.register_device<Device>(port, desc);
  }
};

template <>
struct DeviceBinding<sys_def::BusType::BUS_TYPE_AXIL,
                     sys_def::DeviceType::DEVICE_TYPE_IRH> {
  static constexpr bool supported = true;

  using Handler = axil::AXILitePortHandler;
  using Device = axil::AXILiteCLINT;

  template <size_t PortID>
  static auto make_handler(isa_def::system_t *dut) -> std::unique_ptr<Handler> {
    return std::make_unique<Handler>(
        bus_def::AXILPortBinding<PortID>::bind(dut));
  }

  static auto create(DeviceManager &manager, port_id_t port,
                     const sys_def::DeviceDescriptor &desc,
                     const GeneratedDeviceContext &ctx) -> Device * {
    return manager.register_device<Device>(port, desc, sys_def::FREQ,
                                           ctx.timer_irq, ctx.soft_irq);
  }
};

// Generated registration

template <typename DUT, size_t PortID>
auto register_generated_port(DeviceManager &manager, DUT *dut,
                             const GeneratedDeviceContext &ctx) -> void {
  static_assert(std::is_same_v<DUT, isa_def::system_t>,
                "Generated bus bindings are emitted for isa_def::system_t");

  static_assert(PortID < sys_def::NUM_BUS_DEVICES,
                "PortID exceeds generated BUS_ADDRESS_MAP size");

  constexpr auto desc = sys_def::BUS_ADDRESS_MAP[PortID];
  constexpr auto bus_type = sys_def::BUS_TYPE;
  constexpr auto device_type = desc.type;

  using Binding = DeviceBinding<bus_type, device_type>;

  if constexpr (Binding::supported) {
    auto *device =
        Binding::create(manager, static_cast<port_id_t>(PortID), desc, ctx);

    if (!device) {
      HAL_ERROR("Failed to create generated device '{}' on Port {}", desc.name,
                PortID);
      return;
    }

    manager.register_handler(static_cast<port_id_t>(PortID),
                             Binding::template make_handler<PortID>(dut));

    HAL_DEBUG("Auto-registered generated device '{}' on Port {}", desc.name,
              PortID);
  } else {
    static_assert(always_false_v<Binding>,
                  "No DeviceBinding specialization for generated "
                  "BUS_TYPE + DEVICE_TYPE pair");
  }
}

template <typename DUT, size_t... PortIDs>
auto register_generated_devices_impl(DeviceManager &manager, DUT *dut,
                                     const GeneratedDeviceContext &ctx,
                                     std::index_sequence<PortIDs...>) -> void {
  (register_generated_port<DUT, PortIDs>(manager, dut, ctx), ...);
}

template <typename DUT>
auto register_generated_devices(DeviceManager &manager, DUT *dut,
                                const GeneratedDeviceContext &ctx) -> void {
  register_generated_devices_impl(
      manager, dut, ctx, std::make_index_sequence<sys_def::NUM_BUS_DEVICES>{});
}

} // namespace demu::hal
