cmake_minimum_required(VERSION 3.21)
project(demu LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

set(RISC_DIR "${CMAKE_SOURCE_DIR}/.." CACHE PATH "RISC repository root")

set(SCALA_CONFIG_CMAKE "${RISC_DIR}/build/generated/config.cmake")
set(KCONFIG_CMAKE "${RISC_DIR}/build/generated/kconfig.cmake")

if(NOT EXISTS "${SCALA_CONFIG_CMAKE}")
  message(FATAL_ERROR
    "Missing generated Scala config: ${SCALA_CONFIG_CMAKE}\n"
    "Run `make run` from the repository root first."
  )
endif()

if(NOT EXISTS "${KCONFIG_CMAKE}")
  message(FATAL_ERROR
    "Missing generated Kconfig config: ${KCONFIG_CMAKE}\n"
    "Run `make kconfig` from the repository root first."
  )
endif()

include("${SCALA_CONFIG_CMAKE}")
include("${KCONFIG_CMAKE}")

if(NOT CMAKE_CONFIGURATION_TYPES AND NOT CMAKE_BUILD_TYPE)
  if(DEFINED BUILD_TYPE AND NOT "${BUILD_TYPE}" STREQUAL "")
    set(CMAKE_BUILD_TYPE "${BUILD_TYPE}" CACHE STRING "Build type" FORCE)
  else()
    set(CMAKE_BUILD_TYPE Release CACHE STRING "Build type" FORCE)
  endif()
endif()

set(CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")

message(STATUS "CMake module path: ${CMAKE_MODULE_PATH}")
message(STATUS "Build type: ${CMAKE_BUILD_TYPE}")
message(STATUS "RISC_DIR: ${RISC_DIR}")
message(STATUS "TARGET_FAMILY: ${TARGET_FAMILY}")
message(STATUS "TARGET_ARCH: ${TARGET_ARCH}")
message(STATUS "TOP_MODULE: ${TOP_MODULE}")
message(STATUS "RTL_SOURCE: ${RTL_SOURCE}")
message(STATUS "GEN_DIR: ${GEN_DIR}")
message(STATUS "GENERATED_INCLUDE_DIR: ${GENERATED_INCLUDE_DIR}")
message(STATUS "RUNTIME_DIR: ${RUNTIME_DIR}")
message(STATUS "LINKER_SCRIPT: ${LINKER_SCRIPT}")
message(STATUS "STARTUP_SOURCE: ${STARTUP_SOURCE}")
message(STATUS "ENABLE_SIM: ${ENABLE_SIM}")
message(STATUS "ENABLE_DBG: ${ENABLE_DBG}")
message(STATUS "ENABLE_DIFF: ${ENABLE_DIFF}")
message(STATUS "ENABLE_TESTING: ${ENABLE_TESTING}")
message(STATUS "ENABLE_TRACE: ${ENABLE_TRACE}")
message(STATUS "ENABLE_COVERAGE: ${ENABLE_COVERAGE}")
message(STATUS "ENABLE_COREMARK: ${ENABLE_COREMARK}")
message(STATUS "NUM_THREADS: ${NUM_THREADS}")
message(STATUS "NUM_TRACE_THREADS: ${NUM_TRACE_THREADS}")

include(Helpers)
include(CompilerSetup)
include(Dependencies)
include(SourceCollections)
include(LibraryTargets)

add_subdirectory(tools)

if(ENABLE_TESTING)
  add_subdirectory(tests)
endif()
