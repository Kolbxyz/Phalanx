package org.phalanx.android.ui.screens.room

import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import java.time.ZoneId
import java.time.ZonedDateTime
import org.phalanx.android.core.ai.actions.MessageAction

/**
 * The intent that hands [action] to the app the user picked for it: their calendar, clock, to-do
 * or maps app, or the dialer. Each opens on a screen to check and save, never saving by itself.
 * Null for what Phalanx does itself (reminders, copying a tracking code).
 */
internal fun MessageAction.intent(zone: ZoneId): Intent? = when (this) {
    is MessageAction.Event -> Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, title)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.atZone(zone).millis())
        .apply { end?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it.atZone(zone).millis()) } }
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay)
        .apply { place?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) } }
    is MessageAction.Alarm -> Intent(AlarmClock.ACTION_SET_ALARM)
        .putExtra(AlarmClock.EXTRA_HOUR, at.hour)
        .putExtra(AlarmClock.EXTRA_MINUTES, at.minute)
        .putExtra(AlarmClock.EXTRA_MESSAGE, title)
    is MessageAction.Timer -> Intent(AlarmClock.ACTION_SET_TIMER)
        .putExtra(AlarmClock.EXTRA_LENGTH, minutes * SECONDS_PER_MINUTE)
        .putExtra(AlarmClock.EXTRA_MESSAGE, title)
    // Tasks.org and most to-do apps take shared text as a new task.
    is MessageAction.Task -> Intent.createChooser(
        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, title),
        null
    )
    is MessageAction.Place -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(address)}"))
    is MessageAction.Phone -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
    is MessageAction.Reminder, is MessageAction.Tracking -> null
}

private fun ZonedDateTime.millis(): Long = toInstant().toEpochMilli()

private const val SECONDS_PER_MINUTE = 60
