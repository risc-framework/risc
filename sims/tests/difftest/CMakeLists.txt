find_program(LIT_EXECUTABLE NAMES lit lit.py REQUIRED)

configure_file(
  ${CMAKE_CURRENT_SOURCE_DIR}/lit.site.cfg.py.in
  ${CMAKE_CURRENT_BINARY_DIR}/lit.site.cfg.py
  @ONLY
)

add_custom_target(check-difftest
  COMMAND ${LIT_EXECUTABLE} -v ${CMAKE_CURRENT_BINARY_DIR}
  DEPENDS ${DEMU_DIFF_TARGET} demu-runtime-linker
  COMMENT "Running difftest test suite using lit..."
  USES_TERMINAL
)

add_custom_target(check-difftest-single
  COMMAND ${LIT_EXECUTABLE} -v -j 1 ${CMAKE_CURRENT_BINARY_DIR}
  DEPENDS ${DEMU_DIFF_TARGET} demu-runtime-linker
  COMMENT "Running difftest test suite (single-threaded)..."
  USES_TERMINAL
)

set_target_properties(check-difftest PROPERTIES FOLDER "Tests")
set_target_properties(check-difftest-single PROPERTIES FOLDER "Tests")
