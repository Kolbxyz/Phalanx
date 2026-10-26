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

add_library(phalanx_llm SHARED phalanx_llm.cpp cpu_backend.cpp)
target_include_directories(phalanx_llm PRIVATE llama.cpp/include llama.cpp/ggml/include)
target_link_libraries(phalanx_llm PRIVATE llama ggml)

# Speech to text: only whisper.cpp's library sources, from its pinned submodule, built against
# llama.cpp's ggml above, so the app ships one ggml and one set of CPU backends. whisper.cpp's own
# ggml copy, tools and examples are never built. ANEForge is Apple-only and compiles to a stub.
add_library(phalanx_stt SHARED
        phalanx_stt.cpp
        cpu_backend.cpp
        whisper.cpp/src/whisper.cpp
        whisper.cpp/src/aneforge/whisper-aneforge.cpp)
target_include_directories(phalanx_stt PRIVATE whisper.cpp/include whisper.cpp/src llama.cpp/ggml/include)
target_compile_definitions(phalanx_stt PRIVATE WHISPER_VERSION="1.9.5")
target_link_libraries(phalanx_stt PRIVATE ggml)

# Offline translation: Bergamot, the engine of Firefox's translations, from its pinned submodule
# (nested submodules: .github/scripts/init-bergamot.sh). Static, with the Arm int8 kernels (ruy);
# only what phalanx_mt links is built, none of Marian's tools or tests.
set(BUILD_SHARED_LIBS OFF)
set(COMPILE_TESTS OFF CACHE BOOL "" FORCE)
set(GIT_SUBMODULE OFF CACHE BOOL "" FORCE)
# Its sentence splitter needs PCRE2 (BSD), which it would download while building: it comes from a
# pinned submodule instead, static and without JIT, so nothing is fetched and no memory is executable.
set(PCRE2_BUILD_PCRE2GREP OFF CACHE BOOL "" FORCE)
set(PCRE2_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(PCRE2_SHOW_REPORT OFF CACHE BOOL "" FORCE)
set(PCRE2_STATIC_PIC ON CACHE BOOL "" FORCE)
add_subdirectory(pcre2 EXCLUDE_FROM_ALL)
set(SSPLIT_USE_INTERNAL_PCRE2 OFF CACHE BOOL "" FORCE)
set(PCRE2_LIBRARIES pcre2-8-static CACHE STRING "" FORCE)
set(PCRE2_INCLUDE_DIRS ${CMAKE_CURRENT_BINARY_DIR}/pcre2 CACHE PATH "" FORCE)
set(BUILD_ARCH armv8-a CACHE STRING "" FORCE)
add_subdirectory(bergamot-translator EXCLUDE_FROM_ALL)
# Pathie, Marian's path library, calls iconv() and glob(), which Android only has from API 28.
# Bergamot never globs, and with UTF-8 assumed it never converts names either; weak references
# let it link on Android 8 without ever calling them.
if(ANDROID)
    target_compile_definitions(pathie-cpp PRIVATE PATHIE_ASSUME_UTF8_ON_UNIX __ANDROID_UNAVAILABLE_SYMBOLS_ARE_WEAK__)
    target_compile_options(pathie-cpp PRIVATE -Wno-unguarded-availability)
endif()
add_library(phalanx_mt SHARED phalanx_mt.cpp)
target_link_libraries(phalanx_mt PRIVATE bergamot-translator)
