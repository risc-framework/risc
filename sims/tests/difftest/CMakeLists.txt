find_program(LIT_EXECUTABLE NAMES lit lit.py REQUIRED)

set(DEMU_RUNTIME_LINKER
  ${CMAKE_CURRENT_BINARY_DIR}/runtime/bare-metal/riscv32/linker.ld
)

set(DEMU_RUNTIME_LINKER_TEMPLATE
  ${CMAKE_SOURCE_DIR}/runtime/bare-metal/riscv32/linker.ld.in
)

set(DEMU_SYS_DEF_HEADER
  ${GEN_DIR}/include/demu/generated/sys_def.hh
)

set(DEMU_ISA_DEF_HEADER
  ${GEN_DIR}/include/demu/generated/isa_def.hh
)

add_custom_command(
  OUTPUT ${DEMU_RUNTIME_LINKER}
  COMMAND ${CMAKE_COMMAND} -E make_directory
    ${CMAKE_CURRENT_BINARY_DIR}/runtime/bare-metal/riscv32
  COMMAND $<TARGET_FILE:demu-gen-linker>
    --template ${DEMU_RUNTIME_LINKER_TEMPLATE}
    --output ${DEMU_RUNTIME_LINKER}
  DEPENDS
    demu-gen-linker
    ${DEMU_SYS_DEF_HEADER}
    ${DEMU_ISA_DEF_HEADER}
    ${DEMU_RUNTIME_LINKER_TEMPLATE}
  COMMENT "Generating bare-metal linker script from demu/generated/sys_def.hh"
  VERBATIM
)

add_custom_target(demu-runtime-linker
  DEPENDS ${DEMU_RUNTIME_LINKER}
)

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
set_target_properties(demu-runtime-linker PROPERTIES FOLDER "Tests")
