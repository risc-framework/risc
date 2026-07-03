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

  if("${TARGET_FAMILY}" STREQUAL "")
    message(FATAL_ERROR "TARGET_FAMILY not set!")
  endif()

  if("${TARGET_ARCH}" STREQUAL "")
    message(FATAL_ERROR "TARGET_ARCH not set!")
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
