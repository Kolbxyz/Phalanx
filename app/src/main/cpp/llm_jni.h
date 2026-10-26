// What the language model's JNI entry points share: errors, text across JNI, the chat template
// and streaming a reply. Nothing is ever logged.

#pragma once

#include <jni.h>

#include <cstdint>
#include <string>

#include "llama.h"
#include "llm_engine.h"

void silentLog(ggml_log_level, const char *, void *);

// Throws IllegalStateException with [message], unless an exception is already pending.
void fail(JNIEnv *env, const char *message);

std::string textOf(JNIEnv *env, jbyteArray bytes);

// The model's own chat template, filled in with the two messages; empty if it has none or the
// template is one llama.cpp cannot apply.
std::string promptOf(llama_model *model, const std::string &system, const std::string &user);

// Greedy sampling held to [grammar] (GBNF, rule "root") unless it is null; null when the grammar
// cannot be read. The caller frees it.
llama_sampler *samplerOf(JNIEnv *env, const llama_vocab *vocab, jbyteArray grammar);

// Samples up to [maxNewTokens] after a prompt already in the context, sending each piece to [sink]'s
// onToken. True when it ran to its end; false when cancelled, stopped by the sink, or on failure.
bool streamReply(JNIEnv *env, Engine *engine, llama_sampler *sampler, int32_t maxNewTokens, jobject sink);

// The vision model (llama.cpp's mtmd), loaded on the engine on first use. Defined in phalanx_vision.cpp.
void silenceVision();

void releaseVision(Engine *engine);
