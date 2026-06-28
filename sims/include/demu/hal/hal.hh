#pragma once

#include "allocator.hh"
#include "device_manager.hh"
#include "device_registry.hh"
#include "hardware.hh"
#include "interrupt.hh"
#include "port_handler.hh"

// Peripherals
// SRAM
#include "peripheral/sram/sram.hh"

// UART
#include "peripheral/uart/uart.hh"

// Bus
// AXI4-Lite
#include "bus/axil/irh/clint.hh"
#include "bus/axil/port_handler.hh"
#include "bus/axil/slave.hh"
#include "bus/axil/sram.hh"
#include "bus/axil/uart.hh"

// AXI4-Full
#include "bus/axif/irh/clint.hh"
#include "bus/axif/port_handler.hh"
#include "bus/axif/slave.hh"
#include "bus/axif/sram.hh"
#include "bus/axif/uart.hh"
