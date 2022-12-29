cmake_minimum_required(VERSION 3.23)
project(tonemap)

set(CMAKE_C_STANDARD 99)

include_directories(include)

add_executable(tonemap
        opencl.h
        include/double.h
        include/gamma_correction_filter.cl
        include/rgba.h)
set_target_properties(tonemap PROPERTIES LINKER_LANGUAGE C)
