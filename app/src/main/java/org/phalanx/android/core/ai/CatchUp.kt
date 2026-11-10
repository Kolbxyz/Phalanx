package org.phalanx.android.core.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * One room, caught up on: what matters in what the user missed, what is waiting on them, and what
 * they said they would do. [waiting] and [promised] are null when there is none.
 */
@Serializable
data class CatchUp(val points: List<String>, val waiting: String?, val promised: String?)

/**
 * One message of the room to catch up on, read already or not. [toReader] when it mentions the
 * reader or answers one of their messages.
 */
data class CatchUpLine(
    val sender: String,
    val text: String,
    val isOwn: Boolean,
    val isUnread: Boolean,
    val toReader: Boolean
)

/** What the model writes, held to this shape by the grammar of [CatchUpPrompt]. */
@Serializable
private data class RawCatchUp(
    val points: List<String>,
    @SerialName("ask_line") val askLine: Int = 0,
    val ask: String? = null,
    @SerialName("promise_line") val promiseLine: Int = 0,
    val promise: String? = null
)

/**
 * The reply as a [CatchUp]; null when it is not the JSON asked for. What waits on the reader is
 * kept only when it comes from one of the lines [asks], and what they promised from one of
 * [promises]: the model picks among the messages the code found, and may not add its own.
 */
internal fun catchUpOf(reply: String, asks: Set<Int>, promises: Set<Int>): CatchUp? = try {
    val raw = catchUpJson.decodeFromString<RawCatchUp>(reply)
    CatchUp(
        raw.points.map(String::trim).filter(String::isNotEmpty),
        raw.ask.takeIf { raw.askLine in asks }.said(),
        raw.promise.takeIf { raw.promiseLine in promises }.said()
    )
} catch (_: SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

private fun String?.said(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private val catchUpJson = Json { ignoreUnknownKeys = true }
