// The fastest ggml CPU backend this processor runs, loaded once for the whole process: the
// language model and the speech model share one ggml, so whichever starts first loads it.
#pragma once

// True once a CPU backend is loaded, now or before; false if none runs on this processor.
bool loadCpuBackend();
