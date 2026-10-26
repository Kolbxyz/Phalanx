package org.phalanx.android.core.ai

/**
 * The context every request runs in: room for SummaryPrompt's budget plus the reply. Ministral 3
 * takes far more, but the KV cache grows with it and a phone has little memory to spare. The same
 * size for every request, so the context and what it holds are kept between them.
 */
internal const val CONTEXT_TOKENS = 6144
