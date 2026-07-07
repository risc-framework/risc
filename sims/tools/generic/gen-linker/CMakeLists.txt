set(DEMU_GEN_LINKER demu-gen-linker)
add_executable(${DEMU_GEN_LINKER})

target_sources(${DEMU_GEN_LINKER} PRIVATE main.cpp)
target_link_libraries(${DEMU_GEN_LINKER} PRIVATE demu Threads::Threads)
set_target_properties(${DEMU_GEN_LINKER} PROPERTIES
  OUTPUT_NAME ${DEMU_GEN_LINKER} 
  RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin/generic
)

install(TARGETS ${DEMU_GEN_LINKER} DESTINATION bin)
