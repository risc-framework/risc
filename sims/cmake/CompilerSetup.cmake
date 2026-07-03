# Compiler setup
if(NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Release)
endif()

if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
  message(STATUS "No build type set — defaulting to Release")
  set(CMAKE_BUILD_TYPE Release CACHE STRING "Build type" FORCE)
endif()

if(CMAKE_BUILD_TYPE STREQUAL "Release")
  add_compile_options(
    -O3
    -march=native
    -mtune=native
    -fomit-frame-pointer
    -funroll-loops
    -finline-functions
    -ffast-math
    -fno-math-errno
    -fno-trapping-math
    -freciprocal-math
    -fno-signed-zeros
    -ffinite-math-only
    -fassociative-math
  )

  find_program(MOLD_LINKER mold)
  find_program(LLD_LINKER lld)

  if(MOLD_LINKER)
    message(STATUS "Using mold linker")
    add_link_options(-fuse-ld=mold)
  elseif(LLD_LINKER)
    message(STATUS "Using lld linker")
    add_link_options(-fuse-ld=lld)
  endif()

elseif(CMAKE_BUILD_TYPE STREQUAL "RelWithDebInfo")
  add_compile_options(
    -O2
    -march=native
    -g
    -fno-omit-frame-pointer
    -funroll-loops
    -finline-functions
  )
else()
  add_compile_options(
    -O0
    -g
    -fno-omit-frame-pointer
    -Wall
    -Wextra
  )
endif()

if(USE_CCACHE)
  find_program(CCACHE_PROGRAM ccache)

  if(CCACHE_PROGRAM)
    set(CCACHE_DIR "${CMAKE_BINARY_DIR}/.ccache")
    set(CMAKE_C_COMPILER_LAUNCHER ${CMAKE_COMMAND} -E env "CCACHE_DIR=${CCACHE_DIR}" ${CCACHE_PROGRAM})
    set(CMAKE_CXX_COMPILER_LAUNCHER ${CMAKE_COMMAND} -E env "CCACHE_DIR=${CCACHE_DIR}" ${CCACHE_PROGRAM})
    message(STATUS "Using ccache: ${CCACHE_PROGRAM}")
  else()
    message(STATUS "ccache requested but not found")
  endif()
endif()
