// JNI bridge between org.phalanx.android.core.ai.LlamaNative and llama.cpp. All text crosses as
// UTF-8 byte arrays, since JNI's own string format cannot carry emoji. Nothing is ever logged.

#include <jni.h>

#include <algorithm>
#include <atomic>
#include <exception>
#include <string>
#include <vector>

#include "cpu_backend.h"
#include "llama.h"

namespace {

constexpr int32_t kBatch = 512;
constexpr int32_t kPieceBytes = 256;

struct Engine {
    llama_model *model = nullptr;
    int32_t threads = 4;
    int32_t promptThreads = 4;
    std::atomic<bool> cancelled{false};
};

void silentLog(ggml_log_level, const char *, void *) {}

bool isCancelled(void *flag) { return static_cast<std::atomic<bool> *>(flag)->load(); }

void fail(JNIEnv *env, const char *message) {
    if (env->ExceptionCheck()) return;
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type != nullptr) env->ThrowNew(type, message);
}

std::string textOf(JNIEnv *env, jbyteArray bytes) {
    const jsize size = env->GetArrayLength(bytes);
    std::string text(static_cast<size_t>(size), '\0');
    env->GetByteArrayRegion(bytes, 0, size, reinterpret_cast<jbyte *>(text.data()));
    return text;
}

// The model's own chat template, filled in with the two messages; empty if it has none or the
// template is one llama.cpp cannot apply.
std::string promptOf(llama_model *model, const std::string &system, const std::string &user) {
    const char *tmpl = llama_model_chat_template(model, nullptr);
    if (tmpl == nullptr) return {};
    const llama_chat_message messages[] = {{"system", system.c_str()}, {"user", user.c_str()}};
    std::string prompt(system.size() + user.size() + 1024, '\0');
    int32_t size = llama_chat_apply_template(tmpl, messages, 2, true, prompt.data(),
                                             static_cast<int32_t>(prompt.size()));
    if (size > static_cast<int32_t>(prompt.size())) {
        prompt.resize(static_cast<size_t>(size));
        size = llama_chat_apply_template(tmpl, messages, 2, true, prompt.data(),
                                         static_cast<int32_t>(prompt.size()));
    }
    if (size < 0) return {};
    prompt.resize(static_cast<size_t>(size));
    return prompt;
}

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

// True once the whole prompt is decoded; false when it was cancelled or llama.cpp refused it.
bool decodePrompt(Engine *engine, llama_context *context, std::vector<llama_token> &tokens) {
    for (size_t at = 0; at < tokens.size(); at += kBatch) {
        const int32_t count = static_cast<int32_t>(std::min<size_t>(kBatch, tokens.size() - at));
        if (llama_decode(context, llama_batch_get_one(tokens.data() + at, count)) != 0) return false;
        if (engine->cancelled.load()) return false;
    }
    return true;
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
    llama_model_free(engine->model);
    delete engine;
}

// Streams the reply to [sink] piece by piece. Returns true if it ran to its end, false if it was
// cancelled, by cancel() or by the sink returning false.
JNIEXPORT jboolean JNICALL Java_org_phalanx_android_core_ai_LlamaNative_complete(
        JNIEnv *env, jobject, jlong handle, jbyteArray systemBytes, jbyteArray userBytes,
        jint contextSize, jint maxNewTokens, jobject sink) {
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
        llama_context *context = llama_init_from_model(engine->model, params);
        if (context == nullptr) {
            fail(env, "Not enough memory to run the model");
            return JNI_FALSE;
        }
        llama_set_abort_callback(context, isCancelled, &engine->cancelled);
        llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
        llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

        const jmethodID onToken = env->GetMethodID(env->GetObjectClass(sink), "onToken", "([B)Z");
        bool finished = decodePrompt(engine, context, tokens);
        if (!finished && !engine->cancelled.load()) fail(env, "The model could not read the conversation");
        char piece[kPieceBytes];
        for (int32_t produced = 0; finished && produced < maxNewTokens; produced++) {
            llama_token token = llama_sampler_sample(sampler, context, -1);
            if (llama_vocab_is_eog(vocab, token)) break;
            const int32_t size = llama_token_to_piece(vocab, token, piece, kPieceBytes, 0, false);
            if (size > 0) {
                jbyteArray bytes = env->NewByteArray(size);
                env->SetByteArrayRegion(bytes, 0, size, reinterpret_cast<const jbyte *>(piece));
                const jboolean more = env->CallBooleanMethod(sink, onToken, bytes);
                env->DeleteLocalRef(bytes);
                if (env->ExceptionCheck() || !more) {
                    finished = false;
                    break;
                }
            }
            if (llama_decode(context, llama_batch_get_one(&token, 1)) != 0) {
                finished = false;
                if (!engine->cancelled.load()) fail(env, "The model stopped unexpectedly");
            }
        }
        llama_sampler_free(sampler);
        llama_free(context);
        return finished ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception &) {
        fail(env, "The model ran out of memory");
        return JNI_FALSE;
    }
}

}  // extern "C"
