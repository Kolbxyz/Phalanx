package org.phalanx.android.core.ai

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** What one line of Claude's event stream brings. */
internal sealed interface ClaudeEvent {
    /** More of the reply. */
    data class Text(val text: String) : ClaudeEvent

    /** The reply ended, for [reason]: "end_turn" when it is whole. */
    data class Stopped(val reason: String) : ClaudeEvent

    /** The API gave up midway, with an error of [type]. */
    data class Failed(val type: String) : ClaudeEvent
}

/**
 * The body of the request for [prompt]: streamed, and held to the prompt's JSON schema when it has
 * one. At medium effort: at low, Haiku answers French conversations in English.
 */
internal fun claudeRequest(prompt: Prompt): String = buildJsonObject {
    put("model", CLAUDE_MODEL)
    put("max_tokens", MAX_TOKENS)
    put("stream", true)
    put("system", listOfNotNull(prompt.system, prompt.schemaRule.takeIf { prompt.schema != null }).joinToString("\n"))
    putJsonArray("messages") {
        addJsonObject {
            put("role", "user")
            put("content", prompt.user)
        }
    }
    putJsonObject("output_config") {
        put("effort", "medium")
        prompt.schema?.let { schema ->
            putJsonObject("format") {
                put("type", "json_schema")
                put("schema", schema)
            }
        }
    }
}.toString()

/** The event on [line] of the stream; null for the lines that carry none this app reads. */
internal fun claudeEventOf(line: String): ClaudeEvent? {
    val data = dataOf(line) ?: return null
    return when (data.text("type")) {
        "content_block_delta" -> data.child("delta")?.takeIf { it.text("type") == "text_delta" }
            ?.text("text")?.let(ClaudeEvent::Text)
        "message_delta" -> data.child("delta")?.text("stop_reason")?.let(ClaudeEvent::Stopped)
        "error" -> ClaudeEvent.Failed(data.child("error")?.text("type") ?: "error")
        else -> null
    }
}

/** Why the reply cannot be used, when this event ends it badly: cut short, declined, or an API error. */
internal fun ClaudeEvent.failure(): CloudFailedException? = when (this) {
    is ClaudeEvent.Text -> null
    is ClaudeEvent.Stopped -> if (reason == END_TURN) null else CloudFailedException(reason)
    is ClaudeEvent.Failed -> CloudFailedException(type)
}

private fun dataOf(line: String): JsonObject? = if (!line.startsWith(DATA)) {
    null
} else {
    try {
        Json.parseToJsonElement(line.removePrefix(DATA)) as? JsonObject
    } catch (_: SerializationException) {
        null
    }
}

private fun JsonObject.child(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/** The model every cloud request is written by. */
internal const val CLAUDE_MODEL = "claude-haiku-5-5"

// Far above any reply asked for here: Claude's thinking is counted in it too.
private const val MAX_TOKENS = 4_000
private const val DATA = "data: "
private const val END_TURN = "end_turn"

/** A JSON schema for a value that is text or null. */
internal fun textOrNull(): JsonElement = buildJsonObject {
    putJsonArray("anyOf") {
        addJsonObject { put("type", "string") }
        addJsonObject { put("type", "null") }
    }
}
