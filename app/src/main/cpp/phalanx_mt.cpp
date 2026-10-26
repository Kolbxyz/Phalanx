// JNI bridge between org.phalanx.android.core.ai.translation.BergamotNative and Bergamot, the
// offline translation engine Firefox uses. One handle is one language pair. Text crosses as UTF-8
// byte arrays. Bergamot's own logging is off, and nothing is ever logged here.

#include <jni.h>

#include <exception>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "translator/parser.h"
#include "translator/response_options.h"
#include "translator/service.h"

namespace {

using marian::bergamot::BlockingService;
using marian::bergamot::ResponseOptions;
using marian::bergamot::TranslationModel;

struct Pair {
    std::unique_ptr<BlockingService> service;
    std::shared_ptr<TranslationModel> model;
};

void fail(JNIEnv *env, const char *message) {
    if (env->ExceptionCheck()) return;
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type != nullptr) env->ThrowNew(type, message);
}

std::string stringOf(JNIEnv *env, jstring value) {
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string text(chars);
    env->ReleaseStringUTFChars(value, chars);
    return text;
}

// The settings Firefox translates with: greedy search over the 8-bit model, its shortlist of likely
// words, one sentence batch at a time.
std::string configOf(const std::string &model, const std::string &shortlist, const std::string &sourceVocab,
                     const std::string &targetVocab, int threads) {
    return "models:\n  - " + model +
           "\nvocabs:\n  - " + sourceVocab + "\n  - " + targetVocab +
           "\nshortlist:\n  - " + shortlist + "\n  - false"
           "\nbeam-size: 1\nnormalize: 1.0\nword-penalty: 0\nmax-length-break: 128\nmini-batch-words: 1024"
           "\nworkspace: 128\nmax-length-factor: 2.0\nskip-cost: true\ngemm-precision: int8shiftAlphaAll"
           "\nquiet: true\nquiet-translation: true\ncpu-threads: " + std::to_string(threads) + "\n";
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_org_phalanx_android_core_ai_translation_BergamotNative_load(
        JNIEnv *env, jobject, jstring model, jstring shortlist, jstring sourceVocab, jstring targetVocab,
        jint threads) {
    try {
        const std::string config = configOf(stringOf(env, model), stringOf(env, shortlist),
                                            stringOf(env, sourceVocab), stringOf(env, targetVocab), threads);
        auto pair = std::make_unique<Pair>();
        pair->service = std::make_unique<BlockingService>(BlockingService::Config{});
        pair->model = std::make_shared<TranslationModel>(
                marian::bergamot::parseOptionsFromString(config, /*validate=*/false));
        return reinterpret_cast<jlong>(pair.release());
    } catch (const std::exception &) {
        fail(env, "The translation model could not be loaded");
        return 0;
    }
}

JNIEXPORT void JNICALL Java_org_phalanx_android_core_ai_translation_BergamotNative_free(
        JNIEnv *, jobject, jlong handle) {
    delete reinterpret_cast<Pair *>(handle);
}

// The text, translated whole: Bergamot splits it into sentences itself and keeps line breaks.
JNIEXPORT jbyteArray JNICALL Java_org_phalanx_android_core_ai_translation_BergamotNative_translate(
        JNIEnv *env, jobject, jlong handle, jbyteArray textBytes) {
    auto *pair = reinterpret_cast<Pair *>(handle);
    try {
        const jsize size = env->GetArrayLength(textBytes);
        std::string text(static_cast<size_t>(size), '\0');
        env->GetByteArrayRegion(textBytes, 0, size, reinterpret_cast<jbyte *>(text.data()));
        std::vector<std::string> sources{std::move(text)};
        const std::vector<ResponseOptions> options(1);
        const std::string translated =
                pair->service->translateMultiple(pair->model, std::move(sources), options).front().target.text;
        jbyteArray result = env->NewByteArray(static_cast<jsize>(translated.size()));
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(translated.size()),
                                reinterpret_cast<const jbyte *>(translated.data()));
        return result;
    } catch (const std::exception &) {
        fail(env, "The text could not be translated");
        return nullptr;
    }
}

}  // extern "C"
