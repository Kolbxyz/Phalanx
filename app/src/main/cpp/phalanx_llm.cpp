// JNI bridge between org.phalanx.android.core.ai.LlamaNative and llama.cpp. All text crosses as
// UTF-8 byte arrays, since JNI's own string format cannot carry emoji. Nothing is ever logged.

#include <jni.h>

#include <exception>
#include <string>
#include <vector>

#include "cpu_backend.h"
#include "llama.h"
#include "llm_engine.h"
#include "llm_jni.h"

namespace {

std::vector<llama_token> tokensOf(const llama_vocab *vocab, const std::string &text) {
    const int32_t length = static_cast<int32_t>(text.size());
    const int32_t needed = -llama_tokenize(vocab, text.c_str(), length, nullptr, 0, true, true);
    if (needed <= 0) return {};
    std::vector<llama_token> tokens(static_cast<size_t>(needed));
    const int32_t count = llama_tokenize(vocab, text.c_str(), length, tokens.data(), needed, true, true);
    if (count < 0) return {};
    tokens.resize(static_cast<size_t>(count));
    return tokens;
}

}  // namespace

extern "C" {

// Loads the CPU backend once, before any model. False if none runs on this processor.
JNIEXPORT jboolean JNICALL Java_org_phalanx_android_core_ai_LlamaNative_loadBackend(JNIEnv *, jobject) {
    llama_log_set(silentLog, nullptr);
    if (!loadCpuBackend()) return JNI_FALSE;
    llama_backend_init();
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL Java_org_phalanx_android_core_ai_LlamaNative_load(
        JNIEnv *env, jobject, jstring path, jint threads, jint promptThreads) {
    llama_model_params params = llama_model_default_params();
    params.n_gpu_layers = 0;
    const char *file = env->GetStringUTFChars(path, nullptr);
    llama_model *model = llama_model_load_from_file(file, params);
    env->ReleaseStringUTFChars(path, file);
    if (model == nullptr) {
        fail(env, "The model could not be loaded");
        return 0;
    }
    auto *engine = new Engine();
    engine->model = model;
    engine->threads = threads;
    engine->promptThreads = promptThreads;
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT void JNICALL Java_org_phalanx_android_core_ai_LlamaNative_cancel(JNIEnv *, jobject, jlong handle) {
    reinterpret_cast<Engine *>(handle)->cancelled.store(true);
}

JNIEXPORT void JNICALL Java_org_phalanx_android_core_ai_LlamaNative_free(JNIEnv *, jobject, jlong handle) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    if (engine->context != nullptr) llama_free(engine->context);
    llama_model_free(engine->model);
    delete engine;
}

// Streams the reply to [sink] piece by piece, held to [grammar] (GBNF, rule "root") unless it is
// null. Returns true if it ran to its end, false if it was cancelled, by cancel() or by the sink
// returning false.
JNIEXPORT jboolean JNICALL Java_org_phalanx_android_core_ai_LlamaNative_complete(
        JNIEnv *env, jobject, jlong handle, jbyteArray systemBytes, jbyteArray userBytes,
        jint contextSize, jint maxNewTokens, jbyteArray grammar, jobject sink) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    engine->cancelled.store(false);
    try {
        const std::string prompt = promptOf(engine->model, textOf(env, systemBytes), textOf(env, userBytes));
        if (prompt.empty()) {
            fail(env, "The model's chat template is not supported");
            return JNI_FALSE;
        }
        const llama_vocab *vocab = llama_model_get_vocab(engine->model);
        std::vector<llama_token> tokens = tokensOf(vocab, prompt);
        if (tokens.empty() || static_cast<int32_t>(tokens.size()) + maxNewTokens > contextSize) {
            fail(env, "The conversation is too long for the model");
            return JNI_FALSE;
        }

        llama_context *context = contextFor(engine, contextSize);
        if (context == nullptr) {
            fail(env, "Not enough memory to run the model");
            return JNI_FALSE;
        }
        llama_sampler *sampler = samplerOf(env, vocab, grammar);
        if (sampler == nullptr) {
            fail(env, "The grammar could not be read");
            return JNI_FALSE;
        }

        bool finished = decodePrompt(engine, tokens, reuse(engine, tokens));
        if (!finished && !engine->cancelled.load()) fail(env, "The model could not read the conversation");
        if (finished) finished = streamReply(env, engine, sampler, maxNewTokens, sink);
        // A reply cut off by its sink leaves the memory as [cached] says; a failed decode does not.
        if (!finished && engine->cached.size() != static_cast<size_t>(llama_memory_seq_pos_max(
                llama_get_memory(context), 0) + 1)) {
            forget(engine);
        }
        llama_sampler_free(sampler);
        return finished ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception &) {
        if (engine->context != nullptr) forget(engine);
        fail(env, "The model ran out of memory");
        return JNI_FALSE;
    }
}

}  // extern "C"
