// A loaded language model and the context it keeps between requests. Nothing is ever logged.

#pragma once

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

#include "llama.h"

// Tokens decoded in one llama_decode call.
constexpr int32_t kBatch = 512;

struct Engine {
    llama_model *model = nullptr;
    int32_t threads = 4;
    int32_t promptThreads = 4;
    std::atomic<bool> cancelled{false};
    // Kept between requests, with [cached] the tokens its memory holds, in order: a prompt that
    // starts the same way as the last one (same instructions, same conversation with a few new
    // messages) only has its new end read.
    llama_context *context = nullptr;
    int32_t contextSize = 0;
    std::vector<llama_token> cached;
};

llama_context *contextFor(Engine *engine, int32_t contextSize);

void forget(Engine *engine);

size_t reuse(Engine *engine, const std::vector<llama_token> &tokens);

bool decodePrompt(Engine *engine, std::vector<llama_token> &tokens, size_t from);
