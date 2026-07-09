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
        f'set(RUNTIME_PROFILE "{cmake_escape(string_value(kconf, "RUNTIME_PROFILE", "bare-metal"))}" CACHE STRING "Runtime profile" FORCE)',
        f'set(LINKER_SCRIPT_NAME "{cmake_escape(string_value(kconf, "LINKER_SCRIPT_NAME", "linker.ld"))}" CACHE STRING "Generated linker script filename" FORCE)',
        f'set(STARTUP_SOURCE_NAME "{cmake_escape(string_value(kconf, "STARTUP_SOURCE_NAME", "start.S"))}" CACHE STRING "Generated startup source filename" FORCE)',
        'set(RUNTIME_DIR "${RUNTIME_ROOT}/${RUNTIME_PROFILE}/${TARGET_FAMILY}" CACHE PATH "Generated runtime directory" FORCE)',
        'set(LINKER_SCRIPT "${RUNTIME_DIR}/${LINKER_SCRIPT_NAME}" CACHE FILEPATH "Generated linker script" FORCE)',
        'set(STARTUP_SOURCE "${RUNTIME_DIR}/${STARTUP_SOURCE_NAME}" CACHE FILEPATH "Generated startup source" FORCE)',
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
