find_package(Threads REQUIRED)
find_library(READLINE_LIB readline REQUIRED)
find_package(spdlog CONFIG REQUIRED)
find_package(verilator REQUIRED)

if(NOT verilator_FOUND)
  message(FATAL_ERROR "Verilator not found. Please install Verilator or set VERILATOR_ROOT")
else()
  set(VERILATOR_ARGS
    -Wall
    -Wno-WIDTH
    -Wno-UNUSED
    -Wno-UNOPTFLAT
    -Wno-DECLFILENAME
    -Wno-PINCONNECTEMPTY
  )

  if(${TARGET_ARCH} STREQUAL "rv32i")
    set(__ISA_RV32I__ TRUE CACHE INTERNAL "rv32i is available")
    add_compile_definitions(__ISA_RV32I__)
  elseif(${TARGET_ARCH} STREQUAL "rv32im")
    set(__ISA_RV32IM__ TRUE CACHE INTERNAL "rv32im is available")
    add_compile_definitions(__ISA_RV32IM__)
  else()
    message(FATAL_ERROR "Unsupported ISA: ${TARGET_ARCH}. Supported ISAs: rv32i, rv32im")
  endif()

  add_definitions(-DNUM_THREADS=${NUM_THREADS})

  if(ENABLE_TRACE)
    list(APPEND VERILATOR_ARGS --trace)
    add_definitions(-DENABLE_TRACE)
  endif()

  if(ENABLE_COVERAGE)
    list(APPEND VERILATOR_ARGS --coverage)
  endif()

endif()
