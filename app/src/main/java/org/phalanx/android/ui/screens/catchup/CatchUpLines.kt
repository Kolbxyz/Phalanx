package org.phalanx.android.ui.screens.catchup

import org.phalanx.android.core.ai.CatchUpLine
import org.phalanx.android.data.model.MessageBody
import org.phalanx.android.data.model.TextMarks
import org.phalanx.android.data.model.TimelineEntry
import org.phalanx.android.data.repository.AccountProfile
import org.phalanx.android.ui.screens.room.summaryOf

/**
 * A room's messages as the model reads them: [before], read already, then [unread]. The reader's
 * own are signed [you]. One is addressed to the reader when it answers one of theirs, mentions
 * them, or says their name; [label] resolves a string resource.
 */
internal fun catchUpLines(
    before: List<TimelineEntry.Message>,
    unread: List<TimelineEntry.Message>,
    you: String,
    reader: AccountProfile?,
    label: (Int) -> String
): List<CatchUpLine> {
    val own = (before + unread).filter { it.isOwn }.mapNotNull { it.eventId }.toSet()
    val named = reader?.let(::namedAs)
    fun line(message: TimelineEntry.Message, isUnread: Boolean): CatchUpLine {
        val text = summaryOf(message.body, label)
        val toReader = !message.isOwn && (
            message.replyTo?.eventId in own ||
                reader != null && message.body.mentions(reader.userId) ||
                named?.containsMatchIn(text) == true
            )
        return CatchUpLine(if (message.isOwn) you else message.senderName, text, message.isOwn, isUnread, toReader)
    }
    return before.map { line(it, false) } + unread.map { line(it, true) }
}

private fun MessageBody.mentions(userId: String): Boolean {
    val text = when (this) {
        is MessageBody.Text -> this
        is MessageBody.Image -> caption
        is MessageBody.Video -> caption
        is MessageBody.File -> caption
        else -> null
    }
    return text?.html?.contains("${TextMarks.MENTION}=\"$userId\"") == true
}

// People are called by their first name, as a word of its own and in any case; a shorter one
// would match ordinary words.
private fun namedAs(reader: AccountProfile): Regex? {
    val first = reader.firstName()?.takeIf { it.length >= MIN_NAME_CHARS } ?: return null
    return Regex("(?<![\\p{L}\\p{N}])${Regex.escape(first)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
}

/** What the others call the reader: the first word of their display name, when they have one. */
internal fun AccountProfile.firstName(): String? =
    displayName?.trim()?.substringBefore(' ')?.takeIf(String::isNotEmpty)

private const val MIN_NAME_CHARS = 3
