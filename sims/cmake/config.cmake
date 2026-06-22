# config.cmake 

# compilation configurations
option(USE_CCACHE "Use ccache to speed up recompilation" ON)

# settings
set(TARGET_FAMILY "riscv32")
set(TARGET_ARCH "rv32im")
set(TOP_MODULE "top")
set(RTL_SOURCE "${CMAKE_SOURCE_DIR}/../build/${TOP_MODULE}.sv")
set(GEN_DIR "${CMAKE_SOURCE_DIR}/../build")

option(ENABLE_SIM "Enable simulator" ON)
option(ENABLE_DBG "Enable debugger" ON)
option(ENABLE_DIFF "Enable difftest" ON)

# options
set(NUM_THREADS 1)
set(NUM_TRACE_THREADS 2)
option(ENABLE_TESTING "Enable Testing" ON)
option(ENABLE_TRACE "Enable VCD tracing" ON)
option(ENABLE_COVERAGE "Enable coverage collection" ON)

# benchmarks
option(ENABLE_COREMARK "Enable CoreMark" OFF)
