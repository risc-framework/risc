# demu

add_library(demu
  ${DEMU_SOURCES}
  ${DEMU_HEADERS}
  ${GEN_HEADERS}
)

verilate(demu
  SOURCES ${RTL_SOURCE}
  VERILATOR_ARGS
    -Wall
    -Wno-WIDTH
    -Wno-UNUSED
    -Wno-UNOPTFLAT
    -Wno-DECLFILENAME
    -Wno-PINCONNECTEMPTY
    -j 0
    --top-module ${TOP_MODULE}

    --x-assign fast
    --x-initial fast
    --noassert
    --inline-mult 0
    --unroll-count 256
    --unroll-stmts 100000

    --output-split 2000
    --output-split-cfuncs 2000
    --output-split-ctrace 2000

    -CFLAGS "-O3 -march=native -funroll-loops -fomit-frame-pointer -Wno-unused-variable -Wno-bool-operation -Wno-parentheses-equality"

  PREFIX V${TOP_MODULE}
  TRACE_THREADS ${NUM_TRACE_THREADS}
  THREADS ${NUM_THREADS}
)

target_include_directories(demu PUBLIC
  $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
  $<INSTALL_INTERFACE:include>
  "${GEN_DIR}/include"
)

set_target_properties(demu PROPERTIES
  ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
  LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
)

# spdlog
target_link_libraries(demu PUBLIC spdlog::spdlog)
