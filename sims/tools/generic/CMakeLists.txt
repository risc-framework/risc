add_subdirectory(gen-linker)

set(DEMU_RUNTIME_DIR
  ${CMAKE_BINARY_DIR}/runtime/bare-metal/${TARGET_FAMILY}
)

set(DEMU_RUNTIME_LINKER
  ${DEMU_RUNTIME_DIR}/linker.ld
)

set(DEMU_RUNTIME_START
  ${DEMU_RUNTIME_DIR}/start.S
)

set(DEMU_RUNTIME_LINKER_TEMPLATE
  ${CMAKE_SOURCE_DIR}/runtime/bare-metal/${TARGET_FAMILY}/linker.ld.in
)

set(DEMU_RUNTIME_START_SOURCE
  ${CMAKE_SOURCE_DIR}/runtime/bare-metal/${TARGET_FAMILY}/start.S
)

add_custom_command(
  OUTPUT
    ${DEMU_RUNTIME_LINKER}
    ${DEMU_RUNTIME_START}
  COMMAND ${CMAKE_COMMAND} -E make_directory
    ${DEMU_RUNTIME_DIR}
  COMMAND $<TARGET_FILE:demu-gen-linker>
    --template ${DEMU_RUNTIME_LINKER_TEMPLATE}
    --output ${DEMU_RUNTIME_LINKER}
  COMMAND ${CMAKE_COMMAND} -E copy_if_different
    ${DEMU_RUNTIME_START_SOURCE}
    ${DEMU_RUNTIME_START}
  DEPENDS
    demu-gen-linker
    ${GEN_DIR}
    ${DEMU_RUNTIME_LINKER_TEMPLATE}
    ${DEMU_RUNTIME_START_SOURCE}
  COMMENT "Generating bare-metal runtime from demu/generated/sys_def.hh"
  VERBATIM
)

add_custom_target(demu-runtime-linker ALL
  DEPENDS
    ${DEMU_RUNTIME_LINKER}
    ${DEMU_RUNTIME_START}
)
