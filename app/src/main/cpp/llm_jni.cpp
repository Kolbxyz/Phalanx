#include "llm_jni.h"

namespace {

constexpr int32_t kPieceBytes = 256;

}  // namespace

void silentLog(ggml_log_level, const char *, void *) {}

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

llama_sampler *samplerOf(JNIEnv *env, const llama_vocab *vocab, jbyteArray grammar) {
    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    // A grammar (GBNF) keeps the reply to the shape asked for, such as JSON: tokens that would
    // break it are never picked, whatever the model would rather write.
    if (grammar != nullptr) {
        llama_sampler *rules = llama_sampler_init_grammar(vocab, textOf(env, grammar).c_str(), "root");
        if (rules == nullptr) {
            llama_sampler_free(sampler);
            return nullptr;
        }
        llama_sampler_chain_add(sampler, rules);
    }
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    return sampler;
}

bool streamReply(JNIEnv *env, Engine *engine, llama_sampler *sampler, int32_t maxNewTokens, jobject sink) {
    const llama_vocab *vocab = llama_model_get_vocab(engine->model);
    const jmethodID onToken = env->GetMethodID(env->GetObjectClass(sink), "onToken", "([B)Z");
    char piece[kPieceBytes];
    bool finished = true;
    for (int32_t produced = 0; produced < maxNewTokens; produced++) {
        llama_token token = llama_sampler_sample(sampler, engine->context, -1);
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
        if (llama_decode(engine->context, llama_batch_get_one(&token, 1)) != 0) {
            finished = false;
            if (!engine->cancelled.load()) fail(env, "The model stopped unexpectedly");
            break;
        }
        engine->cached.push_back(token);
    }
    return finished;
}
