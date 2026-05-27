# demu

add_library(demu
  ${DEMU_SOURCES}
  ${DEMU_HEADERS}
  ${GEN_HEADERS}
)

add_dependencies(demu)

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
    --top-module ${TARGET_ARCH}_system
    -CFLAGS "-Wno-unused-variable -Wno-bool-operation -Wno-parentheses-equality"

    --output-split 2000
    --output-split-cfuncs 2000
    --output-split-ctrace 2000

  PREFIX V${TARGET_ARCH}_system
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
