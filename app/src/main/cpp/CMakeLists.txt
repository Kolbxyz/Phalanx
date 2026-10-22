cmake_minimum_required(VERSION 3.22.1)
project(phalanx_llm C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# llama.cpp is built from the pinned submodule as static libraries, CPU only: no network access
# at build time (so no KleidiAI download), no GPU backends, and none of its tools or tests.
set(BUILD_SHARED_LIBS OFF)
set(LLAMA_BUILD_COMMON OFF)
set(LLAMA_BUILD_TESTS OFF)
set(LLAMA_BUILD_TOOLS OFF)
set(LLAMA_BUILD_EXAMPLES OFF)
set(LLAMA_BUILD_SERVER OFF)
set(LLAMA_BUILD_APP OFF)
set(GGML_NATIVE OFF)
set(GGML_OPENMP OFF)
set(GGML_CPU_KLEIDIAI OFF)
# Armv8.2 with the dot-product extension: every arm64 phone since about 2018, and what the
# quantised kernels need to run fast.
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod" CACHE STRING "" FORCE)

add_subdirectory(llama.cpp)

add_library(phalanx_llm SHARED phalanx_llm.cpp)
target_include_directories(phalanx_llm PRIVATE llama.cpp/include llama.cpp/ggml/include)
target_link_libraries(phalanx_llm PRIVATE llama)
# 16 KB page alignment, required for apps targeting Android 15 and later.
target_link_options(phalanx_llm PRIVATE -Wl,-z,max-page-size=16384)
