find_program(LIT_EXECUTABLE NAMES lit lit.py REQUIRED)

configure_file(
  "${CMAKE_CURRENT_SOURCE_DIR}/lit.site.cfg.py.in"
  "${CMAKE_CURRENT_BINARY_DIR}/lit.site.cfg.py"
  @ONLY
)

set(DEMU_DIFFTEST_DEPS "")

if(DEFINED DEMU_DIFF_TARGET AND NOT "${DEMU_DIFF_TARGET}" STREQUAL "")
  list(APPEND DEMU_DIFFTEST_DEPS ${DEMU_DIFF_TARGET})
endif()

if(TARGET demu-runtime-linker)
  list(APPEND DEMU_DIFFTEST_DEPS demu-runtime-linker)
endif()

add_custom_target(check-difftest
  COMMAND ${LIT_EXECUTABLE} -v "${CMAKE_CURRENT_BINARY_DIR}"
  DEPENDS ${DEMU_DIFFTEST_DEPS}
  COMMENT "Running difftest test suite using lit..."
  USES_TERMINAL
)

add_custom_target(check-difftest-single
  COMMAND ${LIT_EXECUTABLE} -v -j 1 "${CMAKE_CURRENT_BINARY_DIR}"
  DEPENDS ${DEMU_DIFFTEST_DEPS}
  COMMENT "Running difftest test suite (single-threaded)..."
  USES_TERMINAL
)

set_target_properties(check-difftest PROPERTIES FOLDER "Tests")
set_target_properties(check-difftest-single PROPERTIES FOLDER "Tests")
