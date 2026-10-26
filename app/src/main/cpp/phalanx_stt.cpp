// JNI bridge between org.phalanx.android.core.ai.speech.WhisperNative and whisper.cpp. Audio
// crosses as 16 kHz mono float samples, text as UTF-8 byte arrays. Nothing is ever logged.

#include <jni.h>

#include <atomic>
#include <exception>
#include <string>

#include "cpu_backend.h"
#include "whisper.h"

namespace {

struct Engine {
    whisper_context *context = nullptr;
    int32_t threads = 4;
    std::atomic<bool> cancelled{false};
};

// What the segment callback needs to hand each new piece of text to Kotlin.
struct Sink {
    JNIEnv *env;
    jobject sink;
    jmethodID onSegment;
};

void silentLog(ggml_log_level, const char *, void *) {}

bool isCancelled(void *flag) { return static_cast<std::atomic<bool> *>(flag)->load(); }

void fail(JNIEnv *env, const char *message) {
    if (env->ExceptionCheck()) return;
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type != nullptr) env->ThrowNew(type, message);
}

// Called on the thread that runs whisper_full, which is the JNI caller's: its JNIEnv is valid.
void onNewSegments(whisper_context *context, whisper_state *, int added, void *data) {
    auto *sink = static_cast<Sink *>(data);
    JNIEnv *env = sink->env;
    const int total = whisper_full_n_segments(context);
    for (int i = total - added; i < total && !env->ExceptionCheck(); i++) {
        const std::string text = whisper_full_get_segment_text(context, i);
        jbyteArray bytes = env->NewByteArray(static_cast<jsize>(text.size()));
        env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(text.size()),
                                reinterpret_cast<const jbyte *>(text.data()));
        env->CallVoidMethod(sink->sink, sink->onSegment, bytes);
        env->DeleteLocalRef(bytes);
    }
}

}  // namespace

extern "C" {

// Loads the CPU backend once, before any model. False if none runs on this processor.
JNIEXPORT jboolean JNICALL Java_org_phalanx_android_core_ai_speech_WhisperNative_loadBackend(JNIEnv *, jobject) {
    whisper_log_set(silentLog, nullptr);
    return loadCpuBackend() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL Java_org_phalanx_android_core_ai_speech_WhisperNative_load(
        JNIEnv *env, jobject, jstring path, jint threads) {
    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    params.flash_attn = true;
    const char *file = env->GetStringUTFChars(path, nullptr);
    whisper_context *context = whisper_init_from_file_with_params(file, params);
    env->ReleaseStringUTFChars(path, file);
    if (context == nullptr) {
        fail(env, "The speech model could not be loaded");
        return 0;
    }
    auto *engine = new Engine();
    engine->context = context;
    engine->threads = threads;
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT void JNICALL Java_org_phalanx_android_core_ai_speech_WhisperNative_cancel(JNIEnv *, jobject, jlong handle) {
    reinterpret_cast<Engine *>(handle)->cancelled.store(true);
}

JNIEXPORT void JNICALL Java_org_phalanx_android_core_ai_speech_WhisperNative_free(JNIEnv *, jobject, jlong handle) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    whisper_free(engine->context);
    delete engine;
}

// Writes out [samples] as text, each segment to [sink] as soon as it is decoded. [language] is a
// code such as "fr", or "auto" to detect it. Returns the language heard, or null when cancelled.
JNIEXPORT jstring JNICALL Java_org_phalanx_android_core_ai_speech_WhisperNative_transcribe(
        JNIEnv *env, jobject, jlong handle, jfloatArray samples, jstring language, jobject sink) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    engine->cancelled.store(false);
    try {
        const jsize count = env->GetArrayLength(samples);
        jfloat *audio = env->GetFloatArrayElements(samples, nullptr);
        const char *lang = env->GetStringUTFChars(language, nullptr);
        Sink segments{env, sink, env->GetMethodID(env->GetObjectClass(sink), "onSegment", "([B)V")};

        whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        params.n_threads = engine->threads;
        params.translate = false;
        params.language = lang;
        params.detect_language = false;
        // A voice message is one speaker and one stretch of speech: no timestamps to compute, and
        // no carrying text from one window to the next, which is where repeated lines come from.
        params.no_timestamps = true;
        params.no_context = true;
        params.suppress_nst = true;
        params.print_special = false;
        params.print_progress = false;
        params.print_realtime = false;
        params.print_timestamps = false;
        params.new_segment_callback = onNewSegments;
        params.new_segment_callback_user_data = &segments;
        params.abort_callback = isCancelled;
        params.abort_callback_user_data = &engine->cancelled;

        const int status = whisper_full(engine->context, params, audio, count);
        env->ReleaseStringUTFChars(language, lang);
        env->ReleaseFloatArrayElements(samples, audio, JNI_ABORT);
        if (env->ExceptionCheck()) return nullptr;
        if (engine->cancelled.load()) return nullptr;
        if (status != 0) {
            fail(env, "The voice message could not be transcribed");
            return nullptr;
        }
        return env->NewStringUTF(whisper_lang_str(whisper_full_lang_id(engine->context)));
    } catch (const std::exception &) {
        fail(env, "The speech model ran out of memory");
        return nullptr;
    }
}

}  // extern "C"
