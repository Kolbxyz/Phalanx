#include "llm_engine.h"

#include <algorithm>

namespace {

constexpr int32_t kBatch = 512;

bool isCancelled(void *flag) { return static_cast<std::atomic<bool> *>(flag)->load(); }

}  // namespace

// The engine's context for [contextSize] tokens, made on first use; null when memory runs short.
llama_context *contextFor(Engine *engine, int32_t contextSize) {
    if (engine->context != nullptr && engine->contextSize == contextSize) return engine->context;
    if (engine->context != nullptr) llama_free(engine->context);
    engine->cached.clear();
    llama_context_params params = llama_context_default_params();
    params.n_ctx = static_cast<uint32_t>(contextSize);
    params.n_batch = kBatch;
    params.n_threads = engine->threads;
    params.n_threads_batch = engine->promptThreads;
    // Flash attention over an 8-bit KV cache: half the memory of the default 16-bit one, and
    // less of it to read for every token of a long conversation.
    params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;
    params.type_k = GGML_TYPE_Q8_0;
    params.type_v = GGML_TYPE_Q8_0;
    engine->context = llama_init_from_model(engine->model, params);
    engine->contextSize = contextSize;
    if (engine->context != nullptr) llama_set_abort_callback(engine->context, isCancelled, &engine->cancelled);
    return engine->context;
}

// Empties the memory, after a request that stopped partway left it holding unknown tokens.
void forget(Engine *engine) {
    llama_memory_clear(llama_get_memory(engine->context), false);
    engine->cached.clear();
}

// Keeps the part of the memory [tokens] starts with and drops the rest. Returns how many tokens
// were kept, never all of them: the last one is read again, as its logits pick the first reply token.
size_t reuse(Engine *engine, const std::vector<llama_token> &tokens) {
    const std::vector<llama_token> &cached = engine->cached;
    size_t kept = std::mismatch(cached.begin(), cached.end(), tokens.begin(), tokens.end()).first - cached.begin();
    kept = std::min(kept, tokens.size() - 1);
    // A model whose memory cannot be cut partway (sliding window, recurrent) starts over.
    if (!llama_memory_seq_rm(llama_get_memory(engine->context), 0, static_cast<llama_pos>(kept), -1)) {
        forget(engine);
        return 0;
    }
    engine->cached.resize(kept);
    return kept;
}

// True once the prompt is decoded from [from] on; false when it was cancelled or llama.cpp refused it.
bool decodePrompt(Engine *engine, std::vector<llama_token> &tokens, size_t from) {
    for (size_t at = from; at < tokens.size(); at += kBatch) {
        const int32_t count = static_cast<int32_t>(std::min<size_t>(kBatch, tokens.size() - at));
        if (llama_decode(engine->context, llama_batch_get_one(tokens.data() + at, count)) != 0) return false;
        engine->cached.insert(engine->cached.end(), tokens.begin() + at, tokens.begin() + at + count);
        if (engine->cancelled.load()) return false;
    }
    return true;
}
