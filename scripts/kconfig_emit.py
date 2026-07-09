#!/usr/bin/env python3

from __future__ import annotations

import argparse
import os
from pathlib import Path


def require_kconfiglib():
    try:
        import kconfiglib  # type: ignore
    except ImportError as exc:
        raise SystemExit(
            "missing Python package: kconfiglib\n"
            "install it with:\n"
            "  python3 -m pip install kconfiglib\n"
        ) from exc

    return kconfiglib


def load_kconfig(kconfig_path: Path, config_path: Path, defconfig: bool):
    kconfiglib = require_kconfiglib()

    os.environ["KCONFIG_CONFIG"] = str(config_path)
    kconf = kconfiglib.Kconfig(str(kconfig_path))

    if config_path.exists() and not defconfig:
        kconf.load_config(str(config_path))

    config_path.parent.mkdir(parents=True, exist_ok=True)
    kconf.write_config(str(config_path))

    return kconf


def bool_value(kconf, name: str, default: bool = False) -> bool:
    symbol = kconf.syms.get(name)

    if symbol is None:
        return default

    return symbol.tri_value == 2


def string_value(kconf, name: str, default: str = "") -> str:
    symbol = kconf.syms.get(name)

    if symbol is None:
        return default

    value = symbol.str_value

    if value == "":
        return default

    return value


def int_value(kconf, name: str, default: int) -> int:
    value = string_value(kconf, name, "")

    if value == "":
        return default

    return int(value, 0)


def generator_value(kconf) -> str:
    if bool_value(kconf, "CMAKE_GENERATOR_MAKE", False):
        return "Unix Makefiles"

    return "Ninja"


def sta_tool_value(kconf) -> str:
    if bool_value(kconf, "STA_TOOL_VIVADO", False):
        return "vivado"

    return "yosys"


def rtthread_platform_value(kconf) -> str:
    if bool_value(kconf, "RTTHREAD_PLATFORM_QEMU", False):
        return "qemu"

    return "demu"


def make_bool(value: bool) -> str:
    return "1" if value else "0"


def cmake_bool(value: bool) -> str:
    return "ON" if value else "OFF"


def make_escape(value: str) -> str:
    return value.replace("#", "\\#")


def cmake_escape(value: str) -> str:
    return value.replace("\\", "\\\\").replace('"', '\\"')


def make_path(value: str) -> str:
    if value == "":
        return ""

    if os.path.isabs(value):
        return value

    return f"$(RISC_DIR)/{value}"


def cmake_path(value: str) -> str:
    if value == "":
        return ""

    if os.path.isabs(value):
        return value

    return f"${{RISC_DIR}}/{value}"


def write_make_config(kconf, output: Path) -> None:
    risc_build_dir = string_value(kconf, "RISC_BUILD_DIR", "build")
    gen_dir = string_value(kconf, "GEN_DIR", "build")
    generated_include_dir = string_value(
        kconf, "GENERATED_INCLUDE_DIR", "build/include"
    )
    synth_dir = string_value(kconf, "SYNTH_DIR", "synth")
    sim_build_dir = string_value(kconf, "SIM_BUILD_DIR", "build/sim")
    runtime_root = string_value(kconf, "RUNTIME_ROOT", "build/runtime")

    rtthread_root = string_value(
        kconf,
        "RTTHREAD_ROOT",
        "sims/runtime/rtthread-nano/rt-thread",
    )
    rtthread_package_root = string_value(
        kconf,
        "RTTHREAD_PACKAGE_ROOT",
        "build/runtime/rtthread-nano",
    )

    lines = [
        "# This file is generated from Kconfig.",
        "# It contains build-policy variables only.",
        "# Hardware target facts come from build/generated/config.mk.",
        "",
        f"RISC_BUILD_DIR ?= {make_path(risc_build_dir)}",
        f"GEN_DIR ?= {make_path(gen_dir)}",
        f"GENERATED_INCLUDE_DIR ?= {make_path(generated_include_dir)}",
        f"SYNTH_DIR ?= {make_path(synth_dir)}",
        f"SIM_BUILD_DIR ?= {make_path(sim_build_dir)}",
        f"RUNTIME_ROOT ?= {make_path(runtime_root)}",
        "",
        "RTL_SOURCE ?= $(GEN_DIR)/$(TOP_MODULE).sv",
        "",
        f"BUILD_TYPE ?= {make_escape(string_value(kconf, 'BUILD_TYPE', 'Release'))}",
        f"GENERATOR ?= {make_escape(generator_value(kconf))}",
        f"STA_TOOL ?= {make_escape(sta_tool_value(kconf))}",
        f"USE_CCACHE ?= {make_bool(bool_value(kconf, 'USE_CCACHE', True))}",
        "",
        f"ENABLE_SIM ?= {make_bool(bool_value(kconf, 'ENABLE_SIM', True))}",
        f"ENABLE_DBG ?= {make_bool(bool_value(kconf, 'ENABLE_DBG', True))}",
        f"ENABLE_DIFF ?= {make_bool(bool_value(kconf, 'ENABLE_DIFF', True))}",
        f"ENABLE_TESTING ?= {make_bool(bool_value(kconf, 'ENABLE_TESTING', True))}",
        f"ENABLE_TRACE ?= {make_bool(bool_value(kconf, 'ENABLE_TRACE', True))}",
        f"ENABLE_COVERAGE ?= {make_bool(bool_value(kconf, 'ENABLE_COVERAGE', True))}",
        f"NUM_THREADS ?= {int_value(kconf, 'NUM_THREADS', 1)}",
        f"NUM_TRACE_THREADS ?= {int_value(kconf, 'NUM_TRACE_THREADS', 2)}",
        "",
        f"RUNTIME_PROFILE ?= {make_escape(string_value(kconf, 'RUNTIME_PROFILE', 'bare-metal'))}",
        f"LINKER_SCRIPT_NAME ?= {make_escape(string_value(kconf, 'LINKER_SCRIPT_NAME', 'linker.ld'))}",
        f"STARTUP_SOURCE_NAME ?= {make_escape(string_value(kconf, 'STARTUP_SOURCE_NAME', 'start.S'))}",
        "RUNTIME_DIR ?= $(RUNTIME_ROOT)/$(RUNTIME_PROFILE)/$(TARGET_FAMILY)",
        "LINKER_SCRIPT ?= $(RUNTIME_DIR)/$(LINKER_SCRIPT_NAME)",
        "STARTUP_SOURCE ?= $(RUNTIME_DIR)/$(STARTUP_SOURCE_NAME)",
        "",
        f"ENABLE_BAREMETAL_RUNTIME ?= {make_bool(bool_value(kconf, 'ENABLE_BAREMETAL_RUNTIME', True))}",
        f"BAREMETAL_CC ?= {make_escape(string_value(kconf, 'BAREMETAL_CC', ''))}",
        f"BAREMETAL_OBJCOPY ?= {make_escape(string_value(kconf, 'BAREMETAL_OBJCOPY', ''))}",
        f"BAREMETAL_OBJDUMP ?= {make_escape(string_value(kconf, 'BAREMETAL_OBJDUMP', ''))}",
        f"BAREMETAL_CFLAGS ?= {make_escape(string_value(kconf, 'BAREMETAL_CFLAGS', '-O3 -ffreestanding -fno-builtin -fno-common -ffunction-sections -fdata-sections -Wall'))}",
        f"BAREMETAL_ASFLAGS ?= {make_escape(string_value(kconf, 'BAREMETAL_ASFLAGS', '-x assembler-with-cpp'))}",
        f"BAREMETAL_LDFLAGS ?= {make_escape(string_value(kconf, 'BAREMETAL_LDFLAGS', '-nostartfiles -nostdlib -Wl,--gc-sections'))}",
        "BAREMETAL_PACKAGE_DIR ?= $(RUNTIME_DIR)",
        "BAREMETAL_STARTUP_OBJ ?= $(BAREMETAL_PACKAGE_DIR)/obj/start.o",
        "BAREMETAL_LINKER_SCRIPT ?= $(LINKER_SCRIPT)",
        "BAREMETAL_EXPORT_MK ?= $(BAREMETAL_PACKAGE_DIR)/baremetal.mk",
        "",
        f"ENABLE_RTTHREAD ?= {make_bool(bool_value(kconf, 'ENABLE_RTTHREAD', False))}",
        f"RTTHREAD_PLATFORM ?= {make_escape(rtthread_platform_value(kconf))}",
        f"RTTHREAD_ROOT ?= {make_path(rtthread_root)}",
        f"RTTHREAD_PACKAGE_ROOT ?= {make_path(rtthread_package_root)}",
        "RTTHREAD_TARGET ?= $(RTTHREAD_PLATFORM)-$(TARGET_FAMILY)-$(TARGET_ARCH)",
        f"RTTHREAD_BSP_DIR_REL ?= {make_escape(string_value(kconf, 'RTTHREAD_BSP_DIR_REL', ''))}",
        f"RTTHREAD_EXTRA_INC_DIRS_REL ?= {make_escape(string_value(kconf, 'RTTHREAD_EXTRA_INC_DIRS_REL', ''))}",
        f"RTTHREAD_PORT_C_SRCS_REL ?= {make_escape(string_value(kconf, 'RTTHREAD_PORT_C_SRCS_REL', ''))}",
        f"RTTHREAD_PORT_ASM_SRCS_REL ?= {make_escape(string_value(kconf, 'RTTHREAD_PORT_ASM_SRCS_REL', ''))}",
        f"RTTHREAD_CC ?= {make_escape(string_value(kconf, 'RTTHREAD_CC', ''))}",
        f"RTTHREAD_AR ?= {make_escape(string_value(kconf, 'RTTHREAD_AR', ''))}",
        f"RTTHREAD_OBJCOPY ?= {make_escape(string_value(kconf, 'RTTHREAD_OBJCOPY', ''))}",
        f"RTTHREAD_OBJDUMP ?= {make_escape(string_value(kconf, 'RTTHREAD_OBJDUMP', ''))}",
        f"RTTHREAD_CFLAGS ?= {make_escape(string_value(kconf, 'RTTHREAD_CFLAGS', '-Os -ffunction-sections -fdata-sections -fno-common -fno-builtin -ffreestanding -Wall'))}",
        f"RTTHREAD_ASFLAGS ?= {make_escape(string_value(kconf, 'RTTHREAD_ASFLAGS', '-x assembler-with-cpp'))}",
        f"RTTHREAD_LDFLAGS ?= {make_escape(string_value(kconf, 'RTTHREAD_LDFLAGS', '-nostartfiles -nostdlib -Wl,--gc-sections'))}",
        "RTTHREAD_PACKAGE_DIR ?= $(RTTHREAD_PACKAGE_ROOT)/$(RTTHREAD_TARGET)",
        "RTTHREAD_LIB ?= $(RTTHREAD_PACKAGE_DIR)/lib/librtthread-nano.a",
        "RTTHREAD_EXPORT_MK ?= $(RTTHREAD_PACKAGE_DIR)/rtthread-nano.mk",
        "",
        f"ENABLE_COREMARK ?= {make_bool(bool_value(kconf, 'ENABLE_COREMARK', False))}",
        f"COREMARK_ENABLE_DEBUG ?= {make_bool(bool_value(kconf, 'COREMARK_ENABLE_DEBUG', False))}",
        f"COREMARK_TOTAL_DATA_SIZE ?= {make_escape(string_value(kconf, 'COREMARK_TOTAL_DATA_SIZE', '2000'))}",
        f"COREMARK_ITERATIONS ?= {make_escape(string_value(kconf, 'COREMARK_ITERATIONS', '1'))}",
        f"COREMARK_EXECS ?= {make_escape(string_value(kconf, 'COREMARK_EXECS', '1'))}",
        "",
    ]

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines), encoding="utf-8")


def write_cmake_config(kconf, output: Path) -> None:
    risc_build_dir = string_value(kconf, "RISC_BUILD_DIR", "build")
    gen_dir = string_value(kconf, "GEN_DIR", "build")
    generated_include_dir = string_value(
        kconf, "GENERATED_INCLUDE_DIR", "build/include"
    )
    sim_build_dir = string_value(kconf, "SIM_BUILD_DIR", "build/sim")
    runtime_root = string_value(kconf, "RUNTIME_ROOT", "build/runtime")

    rtthread_root = string_value(
        kconf,
        "RTTHREAD_ROOT",
        "sims/runtime/rtthread-nano/rt-thread",
    )
    rtthread_package_root = string_value(
        kconf,
        "RTTHREAD_PACKAGE_ROOT",
        "build/runtime/rtthread-nano",
    )

    lines = [
        "# This file is generated from Kconfig.",
        "# It contains build-policy variables only.",
        "# Hardware target facts come from build/generated/config.cmake.",
        "",
        "if(NOT DEFINED RISC_DIR)",
        '  get_filename_component(RISC_DIR "${CMAKE_SOURCE_DIR}/.." ABSOLUTE)',
        "endif()",
        "",
        f'set(RISC_BUILD_DIR "{cmake_escape(cmake_path(risc_build_dir))}" CACHE PATH "Top-level generated build directory" FORCE)',
        f'set(GEN_DIR "{cmake_escape(cmake_path(gen_dir))}" CACHE PATH "Generated RTL directory" FORCE)',
        f'set(GENERATED_INCLUDE_DIR "{cmake_escape(cmake_path(generated_include_dir))}" CACHE PATH "Generated C/C++ include directory" FORCE)',
        f'set(SIM_BUILD_DIR "{cmake_escape(cmake_path(sim_build_dir))}" CACHE PATH "Simulator CMake build directory" FORCE)',
        f'set(RUNTIME_ROOT "{cmake_escape(cmake_path(runtime_root))}" CACHE PATH "Generated runtime root" FORCE)',
        "",
        'set(RTL_SOURCE "${GEN_DIR}/${TOP_MODULE}.sv" CACHE FILEPATH "Generated RTL source" FORCE)',
        "",
        f'set(BUILD_TYPE "{cmake_escape(string_value(kconf, "BUILD_TYPE", "Release"))}" CACHE STRING "Build type" FORCE)',
        f'set(GENERATOR "{cmake_escape(generator_value(kconf))}" CACHE STRING "CMake generator" FORCE)',
        f'set(USE_CCACHE {cmake_bool(bool_value(kconf, "USE_CCACHE", True))} CACHE BOOL "Use ccache" FORCE)',
        "",
        f'set(ENABLE_SIM {cmake_bool(bool_value(kconf, "ENABLE_SIM", True))} CACHE BOOL "Enable simulator" FORCE)',
        f'set(ENABLE_DBG {cmake_bool(bool_value(kconf, "ENABLE_DBG", True))} CACHE BOOL "Enable debugger" FORCE)',
        f'set(ENABLE_DIFF {cmake_bool(bool_value(kconf, "ENABLE_DIFF", True))} CACHE BOOL "Enable difftest" FORCE)',
        f'set(ENABLE_TESTING {cmake_bool(bool_value(kconf, "ENABLE_TESTING", True))} CACHE BOOL "Enable tests" FORCE)',
        f'set(ENABLE_TRACE {cmake_bool(bool_value(kconf, "ENABLE_TRACE", True))} CACHE BOOL "Enable waveform tracing" FORCE)',
        f'set(ENABLE_COVERAGE {cmake_bool(bool_value(kconf, "ENABLE_COVERAGE", True))} CACHE BOOL "Enable coverage" FORCE)',
        f'set(NUM_THREADS {int_value(kconf, "NUM_THREADS", 1)} CACHE STRING "Verilator simulation threads" FORCE)',
        f'set(NUM_TRACE_THREADS {int_value(kconf, "NUM_TRACE_THREADS", 2)} CACHE STRING "Verilator trace threads" FORCE)',
        "",
        f'set(RUNTIME_PROFILE "{cmake_escape(string_value(kconf, "RUNTIME_PROFILE", "bare-metal"))}" CACHE STRING "Bare-metal runtime profile" FORCE)',
        f'set(LINKER_SCRIPT_NAME "{cmake_escape(string_value(kconf, "LINKER_SCRIPT_NAME", "linker.ld"))}" CACHE STRING "Generated linker script filename" FORCE)',
        f'set(STARTUP_SOURCE_NAME "{cmake_escape(string_value(kconf, "STARTUP_SOURCE_NAME", "start.S"))}" CACHE STRING "Generated startup source filename" FORCE)',
        'set(RUNTIME_DIR "${RUNTIME_ROOT}/${RUNTIME_PROFILE}/${TARGET_FAMILY}" CACHE PATH "Generated runtime directory" FORCE)',
        'set(LINKER_SCRIPT "${RUNTIME_DIR}/${LINKER_SCRIPT_NAME}" CACHE FILEPATH "Generated linker script" FORCE)',
        'set(STARTUP_SOURCE "${RUNTIME_DIR}/${STARTUP_SOURCE_NAME}" CACHE FILEPATH "Generated startup source" FORCE)',
        "",
        f'set(ENABLE_BAREMETAL_RUNTIME {cmake_bool(bool_value(kconf, "ENABLE_BAREMETAL_RUNTIME", True))} CACHE BOOL "Build bare-metal runtime package" FORCE)',
        f'set(BAREMETAL_CC "{cmake_escape(string_value(kconf, "BAREMETAL_CC", ""))}" CACHE STRING "Bare-metal C compiler command" FORCE)',
        f'set(BAREMETAL_OBJCOPY "{cmake_escape(string_value(kconf, "BAREMETAL_OBJCOPY", ""))}" CACHE STRING "Bare-metal objcopy command" FORCE)',
        f'set(BAREMETAL_OBJDUMP "{cmake_escape(string_value(kconf, "BAREMETAL_OBJDUMP", ""))}" CACHE STRING "Bare-metal objdump command" FORCE)',
        f'set(BAREMETAL_CFLAGS "{cmake_escape(string_value(kconf, "BAREMETAL_CFLAGS", "-O3 -ffreestanding -fno-builtin -fno-common -ffunction-sections -fdata-sections -Wall"))}" CACHE STRING "Bare-metal C flags" FORCE)',
        f'set(BAREMETAL_ASFLAGS "{cmake_escape(string_value(kconf, "BAREMETAL_ASFLAGS", "-x assembler-with-cpp"))}" CACHE STRING "Bare-metal assembler flags" FORCE)',
        f'set(BAREMETAL_LDFLAGS "{cmake_escape(string_value(kconf, "BAREMETAL_LDFLAGS", "-nostartfiles -nostdlib -Wl,--gc-sections"))}" CACHE STRING "Bare-metal linker flags" FORCE)',
        'set(BAREMETAL_PACKAGE_DIR "${RUNTIME_DIR}" CACHE PATH "Bare-metal package directory" FORCE)',
        'set(BAREMETAL_STARTUP_OBJ "${BAREMETAL_PACKAGE_DIR}/obj/start.o" CACHE FILEPATH "Bare-metal startup object" FORCE)',
        'set(BAREMETAL_LINKER_SCRIPT "${LINKER_SCRIPT}" CACHE FILEPATH "Bare-metal linker script" FORCE)',
        'set(BAREMETAL_EXPORT_MK "${BAREMETAL_PACKAGE_DIR}/baremetal.mk" CACHE FILEPATH "Bare-metal exported Makefile fragment" FORCE)',
        "",
        f'set(ENABLE_RTTHREAD {cmake_bool(bool_value(kconf, "ENABLE_RTTHREAD", False))} CACHE BOOL "Build RT-Thread Nano runtime package" FORCE)',
        f'set(RTTHREAD_PLATFORM "{cmake_escape(rtthread_platform_value(kconf))}" CACHE STRING "RT-Thread platform" FORCE)',
        f'set(RTTHREAD_ROOT "{cmake_escape(cmake_path(rtthread_root))}" CACHE PATH "RT-Thread Nano source root" FORCE)',
        f'set(RTTHREAD_PACKAGE_ROOT "{cmake_escape(cmake_path(rtthread_package_root))}" CACHE PATH "RT-Thread Nano generated package root" FORCE)',
        'set(RTTHREAD_TARGET "${RTTHREAD_PLATFORM}-${TARGET_FAMILY}-${TARGET_ARCH}" CACHE STRING "RT-Thread target name" FORCE)',
        f'set(RTTHREAD_BSP_DIR_REL "{cmake_escape(string_value(kconf, "RTTHREAD_BSP_DIR_REL", ""))}" CACHE STRING "RT-Thread BSP directory relative to RTTHREAD_ROOT" FORCE)',
        f'set(RTTHREAD_EXTRA_INC_DIRS_REL "{cmake_escape(string_value(kconf, "RTTHREAD_EXTRA_INC_DIRS_REL", ""))}" CACHE STRING "Extra RT-Thread include dirs relative to RTTHREAD_ROOT" FORCE)',
        f'set(RTTHREAD_PORT_C_SRCS_REL "{cmake_escape(string_value(kconf, "RTTHREAD_PORT_C_SRCS_REL", ""))}" CACHE STRING "RT-Thread port C sources relative to RTTHREAD_ROOT" FORCE)',
        f'set(RTTHREAD_PORT_ASM_SRCS_REL "{cmake_escape(string_value(kconf, "RTTHREAD_PORT_ASM_SRCS_REL", ""))}" CACHE STRING "RT-Thread port assembly sources relative to RTTHREAD_ROOT" FORCE)',
        f'set(RTTHREAD_CC "{cmake_escape(string_value(kconf, "RTTHREAD_CC", ""))}" CACHE STRING "RT-Thread C compiler command" FORCE)',
        f'set(RTTHREAD_AR "{cmake_escape(string_value(kconf, "RTTHREAD_AR", ""))}" CACHE STRING "RT-Thread archiver command" FORCE)',
        f'set(RTTHREAD_OBJCOPY "{cmake_escape(string_value(kconf, "RTTHREAD_OBJCOPY", ""))}" CACHE STRING "RT-Thread objcopy command" FORCE)',
        f'set(RTTHREAD_OBJDUMP "{cmake_escape(string_value(kconf, "RTTHREAD_OBJDUMP", ""))}" CACHE STRING "RT-Thread objdump command" FORCE)',
        f'set(RTTHREAD_CFLAGS "{cmake_escape(string_value(kconf, "RTTHREAD_CFLAGS", "-Os -ffunction-sections -fdata-sections -fno-common -fno-builtin -ffreestanding -Wall"))}" CACHE STRING "RT-Thread C flags" FORCE)',
        f'set(RTTHREAD_ASFLAGS "{cmake_escape(string_value(kconf, "RTTHREAD_ASFLAGS", "-x assembler-with-cpp"))}" CACHE STRING "RT-Thread assembler flags" FORCE)',
        f'set(RTTHREAD_LDFLAGS "{cmake_escape(string_value(kconf, "RTTHREAD_LDFLAGS", "-nostartfiles -nostdlib -Wl,--gc-sections"))}" CACHE STRING "RT-Thread linker flags" FORCE)',
        'set(RTTHREAD_PACKAGE_DIR "${RTTHREAD_PACKAGE_ROOT}/${RTTHREAD_TARGET}" CACHE PATH "RT-Thread generated package directory" FORCE)',
        'set(RTTHREAD_LIB "${RTTHREAD_PACKAGE_DIR}/lib/librtthread-nano.a" CACHE FILEPATH "RT-Thread static library" FORCE)',
        'set(RTTHREAD_EXPORT_MK "${RTTHREAD_PACKAGE_DIR}/rtthread-nano.mk" CACHE FILEPATH "RT-Thread exported Makefile fragment" FORCE)',
        'set(RTTHREAD_STARTUP_OBJ "${BAREMETAL_STARTUP_OBJ}" CACHE FILEPATH "RT-Thread startup object reused from bare-metal package" FORCE)',
        'set(RTTHREAD_LINKER_SCRIPT "${BAREMETAL_LINKER_SCRIPT}" CACHE FILEPATH "RT-Thread linker script reused from bare-metal package" FORCE)',
        "",
        f'set(ENABLE_COREMARK {cmake_bool(bool_value(kconf, "ENABLE_COREMARK", False))} CACHE BOOL "Enable CoreMark" FORCE)',
        f'set(COREMARK_ENABLE_DEBUG {cmake_bool(bool_value(kconf, "COREMARK_ENABLE_DEBUG", False))} CACHE BOOL "Enable CoreMark debug" FORCE)',
        f'set(COREMARK_TOTAL_DATA_SIZE "{cmake_escape(string_value(kconf, "COREMARK_TOTAL_DATA_SIZE", "2000"))}" CACHE STRING "CoreMark total data size" FORCE)',
        f'set(COREMARK_ITERATIONS "{cmake_escape(string_value(kconf, "COREMARK_ITERATIONS", "1"))}" CACHE STRING "CoreMark iterations" FORCE)',
        f'set(COREMARK_EXECS "{cmake_escape(string_value(kconf, "COREMARK_EXECS", "1"))}" CACHE STRING "CoreMark execs" FORCE)',
        "",
    ]

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--kconfig", default="Kconfig")
    parser.add_argument("--config", default=".config")
    parser.add_argument("--mk", default="build/generated/kconfig.mk")
    parser.add_argument("--cmake", default="build/generated/kconfig.cmake")
    parser.add_argument("--defconfig", action="store_true")
    args = parser.parse_args()

    kconf = load_kconfig(
        kconfig_path=Path(args.kconfig),
        config_path=Path(args.config),
        defconfig=args.defconfig,
    )

    write_make_config(kconf, Path(args.mk))
    write_cmake_config(kconf, Path(args.cmake))


if __name__ == "__main__":
    main()
