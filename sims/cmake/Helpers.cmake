function(print_info MSG COLOR)
  execute_process(COMMAND printf "\\033[1;${COLOR}m${MSG}\\033[0m")
endfunction()

function(make_paths_relative OUT_VAR)
  set(result "")

  foreach(path IN LISTS ARGN)
    file(RELATIVE_PATH rel "${CMAKE_CURRENT_SOURCE_DIR}" "${path}")
    list(APPEND result "${rel}")
  endforeach()

  set(${OUT_VAR} "${result}" PARENT_SCOPE)
endfunction()

function(make_preview_string OUT_VAR LIST_VAR MAX_ITEMS)
  set(preview "")
  set(count 0)

  foreach(item IN LISTS ${LIST_VAR})
    if(count GREATER_EQUAL MAX_ITEMS)
      break()
    endif()

    list(APPEND preview "${item}")
    math(EXPR count "${count} + 1")
  endforeach()

  list(LENGTH ${LIST_VAR} list_length)

  if(list_length GREATER MAX_ITEMS)
    math(EXPR remaining_items "${list_length} - ${MAX_ITEMS}")
    list(APPEND preview "... (+${remaining_items} more)")
  endif()

  set(${OUT_VAR} "${preview}" PARENT_SCOPE)
endfunction()
