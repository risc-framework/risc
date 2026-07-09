find_package(Threads REQUIRED)
find_library(READLINE_LIB readline REQUIRED)
find_package(spdlog CONFIG REQUIRED)
find_package(verilator REQUIRED)

if(NOT verilator_FOUND)
  message(FATAL_ERROR "Verilator not found. Please install Verilator or set VERILATOR_ROOT.")
endif()

if(NOT DEFINED TARGET_FAMILY OR "${TARGET_FAMILY}" STREQUAL "")
  message(FATAL_ERROR "TARGET_FAMILY is not set. Check build/generated/config.cmake.")
endif()

if(NOT DEFINED TARGET_ARCH OR "${TARGET_ARCH}" STREQUAL "")
  message(FATAL_ERROR "TARGET_ARCH is not set. Check build/generated/config.cmake.")
endif()

if(NOT DEFINED TOP_MODULE OR "${TOP_MODULE}" STREQUAL "")
  message(FATAL_ERROR "TOP_MODULE is not set. Check build/generated/config.cmake.")
endif()

if(NOT DEFINED RTL_SOURCE OR "${RTL_SOURCE}" STREQUAL "")
  message(FATAL_ERROR "RTL_SOURCE is not set. Check build/generated/kconfig.cmake.")
endif()

if(NOT DEFINED GEN_DIR OR "${GEN_DIR}" STREQUAL "")
  message(FATAL_ERROR "GEN_DIR is not set. Check build/generated/kconfig.cmake.")
endif()

if(NOT DEFINED GENERATED_INCLUDE_DIR OR "${GENERATED_INCLUDE_DIR}" STREQUAL "")
  message(FATAL_ERROR "GENERATED_INCLUDE_DIR is not set. Check build/generated/kconfig.cmake.")
endif()

if(NOT DEFINED RUNTIME_DIR OR "${RUNTIME_DIR}" STREQUAL "")
  message(FATAL_ERROR "RUNTIME_DIR is not set. Check build/generated/kconfig.cmake.")
endif()

if(NOT DEFINED LINKER_SCRIPT OR "${LINKER_SCRIPT}" STREQUAL "")
  message(FATAL_ERROR "LINKER_SCRIPT is not set. Check build/generated/kconfig.cmake.")
endif()

if(NOT DEFINED STARTUP_SOURCE OR "${STARTUP_SOURCE}" STREQUAL "")
  message(FATAL_ERROR "STARTUP_SOURCE is not set. Check build/generated/kconfig.cmake.")
endif()

if(NOT DEFINED NUM_THREADS OR "${NUM_THREADS}" STREQUAL "")
  set(NUM_THREADS 1 CACHE STRING "Verilator simulation threads" FORCE)
endif()

if(NOT DEFINED NUM_TRACE_THREADS OR "${NUM_TRACE_THREADS}" STREQUAL "")
  set(NUM_TRACE_THREADS 2 CACHE STRING "Verilator trace threads" FORCE)
endif()

if(NOT EXISTS "${RTL_SOURCE}")
  message(FATAL_ERROR
    "RTL file not found: ${RTL_SOURCE}\n"
    "Run `make run` from the repository root first."
  )
endif()

if(NOT EXISTS "${LINKER_SCRIPT}")
  message(FATAL_ERROR
    "Generated linker script not found: ${LINKER_SCRIPT}\n"
    "Run `make run` from the repository root first."
  )
endif()

if(NOT EXISTS "${STARTUP_SOURCE}")
  message(FATAL_ERROR
    "Generated startup source not found: ${STARTUP_SOURCE}\n"
    "Run `make run` from the repository root first."
  )
endif()

set(DEMU_VERILATOR_ARGS
  -Wall
  -Wno-WIDTH
  -Wno-UNUSED
  -Wno-UNOPTFLAT
  -Wno-DECLFILENAME
  -Wno-PINCONNECTEMPTY
)

if(ENABLE_TRACE)
  list(APPEND DEMU_VERILATOR_ARGS --trace)
endif()

if(ENABLE_COVERAGE)
  list(APPEND DEMU_VERILATOR_ARGS --coverage)
endif()
