cmake_minimum_required(VERSION 3.22.1)
project(phalanx_llm C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# llama.cpp is built from the pinned submodule, CPU only: no network access at build time (so no
# KleidiAI download), no GPU backends, and none of its tools or tests.
set(LLAMA_BUILD_COMMON OFF)
set(LLAMA_BUILD_TESTS OFF)
set(LLAMA_BUILD_TOOLS OFF)
set(LLAMA_BUILD_EXAMPLES OFF)
set(LLAMA_BUILD_SERVER OFF)
set(LLAMA_BUILD_APP OFF)
set(GGML_NATIVE OFF)
set(GGML_OPENMP OFF)
set(GGML_CPU_KLEIDIAI OFF)
# One CPU backend library per Arm feature level, from plain Armv8.0 up to SVE2, int8 matrix
# multiply (i8mm) and SME; phalanx_llm.cpp loads the most capable one the phone runs. Reading the
# prompt is most of the wait, and its fastest kernels need i8mm, which one Armv8.2 build cannot use.
# Loading them at run time needs ggml and llama as shared libraries.
set(BUILD_SHARED_LIBS ON)
set(GGML_BACKEND_DL ON)
set(GGML_CPU_ALL_VARIANTS ON)
# 16 KB page alignment for every library, required for apps targeting Android 15 and later.
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

add_subdirectory(llama.cpp)

add_library(phalanx_llm SHARED phalanx_llm.cpp)
target_include_directories(phalanx_llm PRIVATE llama.cpp/include llama.cpp/ggml/include)
target_link_libraries(phalanx_llm PRIVATE llama ggml)
