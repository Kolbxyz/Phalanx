#include "cpu_backend.h"

#include <mutex>

#include "ggml-backend.h"

namespace {

// The CPU backend builds (CMakeLists.txt), most capable first: the first one this processor can
// run is the fastest it has. Loaded by file name, so the linker finds them inside the APK.
constexpr const char *kCpuBackends[] = {
        "libggml-cpu-android_armv9.2_2.so", "libggml-cpu-android_armv9.2_1.so",
        "libggml-cpu-android_armv9.0_1.so", "libggml-cpu-android_armv8.6_1.so",
        "libggml-cpu-android_armv8.2_2.so", "libggml-cpu-android_armv8.2_1.so",
        "libggml-cpu-android_armv8.0_1.so",
};

std::mutex loading;

}  // namespace

bool loadCpuBackend() {
    std::lock_guard<std::mutex> guard(loading);
    // Registering a second CPU backend would offer the same cores twice.
    if (ggml_backend_reg_by_name("CPU") != nullptr) return true;
    for (const char *name : kCpuBackends) {
        if (ggml_backend_load(name) != nullptr) return true;
    }
    return false;
}
