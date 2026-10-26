// JNI bridge for describing an image with the loaded language model and its vision projector,
// through llama.cpp's mtmd library. The image arrives as RGB bytes, already scaled down. Nothing is
// ever logged.

#include <jni.h>

#include <exception>
#include <string>

#include "llm_engine.h"
#include "llm_jni.h"
#include "mtmd-helper.h"
#include "mtmd.h"

namespace {

// The engine's vision projector from [path], loaded once; null when the file is not one that fits
// the loaded model.
mtmd_context *visionFor(Engine *engine, const std::string &path) {
    if (engine->vision != nullptr && engine->visionPath == path) return engine->vision;
    releaseVision(engine);
    mtmd_context_params params = mtmd_context_params_default();
    params.use_gpu = false;
    params.print_timings = false;
    params.warmup = false;
    params.n_threads = engine->promptThreads;
    engine->vision = mtmd_init_from_file(path.c_str(), engine->model, params);
    if (engine->vision != nullptr && !mtmd_support_vision(engine->vision)) releaseVision(engine);
    engine->visionPath = engine->vision != nullptr ? path : std::string();
    return engine->vision;
}

// Reads the prompt with the image into the context. 0 on success, else what went wrong.
const char *readImage(Engine *engine, const std::string &prompt, mtmd_bitmap *image, int32_t room) {
    mtmd_input_chunks *chunks = mtmd_input_chunks_init();
    const mtmd_input_text text{prompt.c_str(), prompt.size(), true, true};
    const mtmd_bitmap *images[] = {image};
    const char *error = nullptr;
    if (mtmd_tokenize(engine->vision, chunks, &text, images, 1) != 0) {
        error = "The image could not be read";
    } else if (static_cast<int32_t>(mtmd_helper_get_n_tokens(chunks)) > room) {
        error = "The image is too large for the model";
    } else {
        llama_pos past = 0;
        if (mtmd_helper_eval_chunks(engine->vision, engine->context, chunks, 0, 0, kBatch, true, &past) != 0) {
            error = "The model could not read the image";
        }
    }
    mtmd_input_chunks_free(chunks);
    return error;
}

}  // namespace

void silenceVision() { mtmd_helper_log_set(silentLog, nullptr); }

void releaseVision(Engine *engine) {
    if (engine->vision != nullptr) mtmd_free(engine->vision);
    engine->vision = nullptr;
    engine->visionPath.clear();
}

extern "C" {

// Streams what the model writes about the image ([width] x [height] RGB bytes) to [sink], answering
// [userBytes], held to [grammar] unless it is null. Returns true if it ran to its end, false if it
// was cancelled.
JNIEXPORT jboolean JNICALL Java_org_phalanx_android_core_ai_LlamaNative_describe(
        JNIEnv *env, jobject, jlong handle, jstring projector, jbyteArray rgb, jint width, jint height,
        jbyteArray systemBytes, jbyteArray userBytes, jint contextSize, jint maxNewTokens, jbyteArray grammar,
        jobject sink) {
    auto *engine = reinterpret_cast<Engine *>(handle);
    engine->cancelled.store(false);
    if (width <= 0 || height <= 0 || env->GetArrayLength(rgb) != width * height * 3) {
        fail(env, "The image could not be read");
        return JNI_FALSE;
    }
    try {
        const char *file = env->GetStringUTFChars(projector, nullptr);
        const std::string path(file);
        env->ReleaseStringUTFChars(projector, file);
        if (visionFor(engine, path) == nullptr) {
            fail(env, "The vision model does not fit the language model");
            return JNI_FALSE;
        }
        if (contextFor(engine, contextSize) == nullptr) {
            fail(env, "Not enough memory to run the model");
            return JNI_FALSE;
        }
        // An image's place in the memory is not a run of tokens, so none of it can be reused.
        forget(engine);
        const std::string user = std::string(mtmd_default_marker()) + "\n" + textOf(env, userBytes);
        const std::string prompt = promptOf(engine->model, textOf(env, systemBytes), user);
        if (prompt.empty()) {
            fail(env, "The model's chat template is not supported");
            return JNI_FALSE;
        }
        const std::string pixels = textOf(env, rgb);
        mtmd_bitmap *image = mtmd_bitmap_init(static_cast<uint32_t>(width), static_cast<uint32_t>(height),
                                              reinterpret_cast<const unsigned char *>(pixels.data()));
        const char *error = readImage(engine, prompt, image, contextSize - maxNewTokens);
        mtmd_bitmap_free(image);
        const llama_vocab *vocab = llama_model_get_vocab(engine->model);
        llama_sampler *sampler = error == nullptr ? samplerOf(env, vocab, grammar) : nullptr;
        if (error == nullptr && sampler == nullptr) error = "The grammar could not be read";
        bool finished = error == nullptr;
        if (finished) {
            finished = streamReply(env, engine, sampler, maxNewTokens, sink);
            llama_sampler_free(sampler);
        } else if (!engine->cancelled.load()) {
            fail(env, error);
        }
        forget(engine);
        return finished ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception &) {
        if (engine->context != nullptr) forget(engine);
        fail(env, "The model ran out of memory");
        return JNI_FALSE;
    }
}

}  // extern "C"
