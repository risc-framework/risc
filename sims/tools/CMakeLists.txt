# Apps
if(ENABLE_SIM)
  add_subdirectory(simulator)
endif()

if(ENABLE_DBG)
  add_subdirectory(debugger)
endif()

if(ENABLE_DIFF)
  add_subdirectory(difftest)
endif()

# Configuration 
print_info("Configuration: \n" "92" "0")
print_info("  ISA: ${TARGET_ARCH}\n" "92" "1")
print_info("  Build Type: ${CMAKE_BUILD_TYPE}\n" "92" "1")
print_info("  C++ Compiler: ${CMAKE_CXX_COMPILER}\n" "92" "1")
print_info("  Verilator: ${VERILATOR_ROOT}\n" "92" "1")
print_info("  RTL Source: ${RTL_SOURCE}\n" "92" "1")
print_info("  Enable Trace: ${ENABLE_TRACE}\n" "94" "2")
print_info("  Enable Coverage: ${ENABLE_COVERAGE}\n" "94" "2")
print_info("  Enable Simulator: ${ENABLE_SIM}\n" "94" "2")
print_info("  Enable Debugger: ${ENABLE_DBG}\n" "94" "2")
print_info("  Enable Difftest: ${ENABLE_DIFF}\n" "94" "2")
