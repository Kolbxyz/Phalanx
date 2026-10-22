package org.phalanx.android.core.matrix

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.phalanx.android.data.model.ArchivedKind

/** What one synced event does to the kept copies. */
internal sealed interface ArchiveChange {
    data class Sent(val kind: ArchivedKind, val text: String) : ArchiveChange

    data class Edited(val target: String, val kind: ArchivedKind, val text: String) : ArchiveChange

    data class Deleted(val target: String) : ArchiveChange
}

/** The event types the archive listens to; kept apart from the SDK's so parsing runs without it. */
internal enum class ArchivedEventType { Message, Sticker, Redaction }

/** Null for an event that changes nothing, or whose [contentJson] can't be read. */
internal fun archiveChange(type: ArchivedEventType, contentJson: String): ArchiveChange? {
    val content = objectOf(contentJson) ?: return null
    return when (type) {
        // Room versions before 11 keep `redacts` at the top level, but servers copy it into the
        // content for every version, which is the only part the SDK hands over.
        ArchivedEventType.Redaction -> content.string("redacts")?.let(ArchiveChange::Deleted)
        ArchivedEventType.Sticker -> ArchiveChange.Sent(ArchivedKind.Sticker, content.string("body").orEmpty())
        ArchivedEventType.Message -> message(content)
    }
}

/** Null for JSON that doesn't parse, or isn't an object. */
private fun objectOf(json: String): JsonObject? = try {
    Json.parseToJsonElement(json) as? JsonObject
} catch (_: SerializationException) {
    null
}

private fun message(content: JsonObject): ArchiveChange? {
    val relation = content["m.relates_to"] as? JsonObject
    if (relation?.string("rel_type") != REPLACE) {
        return ArchiveChange.Sent(kindOf(content), textOf(content, isReply = relation?.get("m.in_reply_to") != null))
    }
    val target = relation.string("event_id")
    val replacement = content["m.new_content"] as? JsonObject
    // An edit missing either part is malformed; it is not a new message either.
    return if (target != null && replacement != null) {
        ArchiveChange.Edited(target, kindOf(replacement), textOf(replacement, isReply = false))
    } else {
        null
    }
}

private fun kindOf(content: JsonObject): ArchivedKind = when (content.string("msgtype")) {
    "m.text", "m.notice", "m.emote" -> ArchivedKind.Text
    "m.image" -> ArchivedKind.Image
    "m.video" -> ArchivedKind.Video
    "m.audio" -> ArchivedKind.Audio
    "m.file" -> ArchivedKind.File
    "m.location" -> ArchivedKind.Location
    else -> ArchivedKind.Other
}

/** The body is the text, or for media its caption or file name. */
private fun textOf(content: JsonObject, isReply: Boolean): String {
    val body = content.string("body").orEmpty()
    return if (isReply) HtmlSanitizer.stripPlainReplyFallback(body) else body
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private const val REPLACE = "m.replace"
