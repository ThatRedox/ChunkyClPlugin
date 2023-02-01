cmake_minimum_required(VERSION 3.23)
project(ChunkyCL)

set(CMAKE_C_STANDARD 99)

include_directories(include)

add_executable(kernel
        opencl.h
        include/block.h
        include/bvh.h
        include/camera.h
        include/constants.h
        include/kernel.h
        include/material.h
        include/octree.h
        include/primitives.h
        include/randomness.h
        include/rayTracer.cl
        include/sky.h
        include/textureAtlas.h
        include/utils.h
        include/rt.h
)
set_target_properties(kernel PROPERTIES LINKER_LANGUAGE C)
