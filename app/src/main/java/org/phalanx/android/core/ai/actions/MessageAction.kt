package org.phalanx.android.core.ai.actions

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Something a message asks or proposes that another app can take over. [title] says what it is. */
sealed interface MessageAction {
    val title: String

    /** For the calendar. [allDay] when the message gave a day but no time. */
    data class Event(
        override val title: String,
        val start: LocalDateTime,
        val end: LocalDateTime?,
        val allDay: Boolean,
        val place: String?
    ) : MessageAction

    /** For the clock app; it can only ring at a time of day, so [at] is meant for today or tomorrow. */
    data class Alarm(override val title: String, val at: LocalDateTime) : MessageAction

    data class Timer(override val title: String, val minutes: Int) : MessageAction

    /** A reminder of the message itself, set in Phalanx. */
    data class Reminder(override val title: String, val at: LocalDateTime) : MessageAction

    data class Task(override val title: String) : MessageAction

    data class Place(override val title: String, val address: String) : MessageAction

    data class Phone(override val title: String, val number: String) : MessageAction

    data class Tracking(override val title: String, val code: String) : MessageAction
}

/** What the model writes, held to this shape by the grammar in [actionsPrompt]. */
@Serializable
internal data class RawAction(
    val type: String,
    val title: String,
    val start: String? = null,
    val end: String? = null,
    val minutes: Int? = null,
    val detail: String? = null
)

private val json = Json { ignoreUnknownKeys = true }

/**
 * The model's reply as actions. One that misses what it needs (an event without a date, a place
 * without an address) or names a date that does not exist is left out rather than guessed at.
 */
internal fun actionsOf(reply: String): List<MessageAction> = try {
    json.decodeFromString<List<RawAction>>(reply).mapNotNull { it.toAction() }.distinct()
} catch (_: SerializationException) {
    emptyList()
} catch (_: IllegalArgumentException) {
    emptyList()
}

private fun RawAction.toAction(): MessageAction? {
    val name = title.trim().ifEmpty { return null }
    val text = detail?.trim()?.takeIf { it.isNotEmpty() }
    return when (type) {
        "event", "alarm", "reminder" -> timedAction(name, text)
        "timer" -> minutes?.takeIf { it in 1..MAX_TIMER_MINUTES }?.let { MessageAction.Timer(name, it) }
        "task" -> MessageAction.Task(name)
        "place" -> text?.let { MessageAction.Place(name, it) }
        "phone" -> text?.takeIf { it.count(Char::isDigit) >= MIN_PHONE_DIGITS }?.let { MessageAction.Phone(name, it) }
        "tracking" -> text?.let { MessageAction.Tracking(name, it) }
        else -> null
    }
}

// An event, alarm or reminder: none of them is anything without its date.
private fun RawAction.timedAction(name: String, text: String?): MessageAction? {
    val (at, allDay) = start?.let(::dateTimeOf) ?: return null
    return when (type) {
        "event" -> MessageAction.Event(name, at, end?.let(::dateTimeOf)?.first?.takeIf { it > at }, allDay, text)
        "alarm" -> MessageAction.Alarm(name, at).takeUnless { allDay }
        else -> MessageAction.Reminder(name, at)
    }
}

/** "2026-10-30T20:00" or "2026-10-30", the second flagged as a whole day; null if it is no real date. */
private fun dateTimeOf(value: String): Pair<LocalDateTime, Boolean>? = try {
    if (value.length == DATE_LENGTH) {
        LocalDate.parse(value).atTime(LocalTime.of(DEFAULT_HOUR, 0)) to true
    } else {
        LocalDateTime.parse(value) to false
    }
} catch (_: DateTimeParseException) {
    null
}

private const val DATE_LENGTH = 10
private const val DEFAULT_HOUR = 9
private const val MAX_TIMER_MINUTES = 24 * 60
private const val MIN_PHONE_DIGITS = 6
