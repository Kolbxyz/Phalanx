package org.phalanx.android.ui.screens.room

import androidx.annotation.StringRes
import java.time.LocalTime
import java.time.ZonedDateTime
import org.phalanx.android.R

/** A one-tap time to be reminded or to send at, worked out from now. */
internal enum class TimePreset(@StringRes val label: Int) {
    InAnHour(R.string.schedule_in_hour),
    Tonight(R.string.schedule_tonight),
    TomorrowMorning(R.string.schedule_tomorrow_morning);

    fun at(now: ZonedDateTime): Long = when (this) {
        InAnHour -> now.plusHours(1)
        Tonight -> now.with(LocalTime.of(EVENING_HOUR, 0))
        TomorrowMorning -> now.plusDays(1).with(LocalTime.of(MORNING_HOUR, 0))
    }.toInstant().toEpochMilli()

    companion object {
        /** The presets that are still ahead of [now]: tonight is gone once the evening has started. */
        fun available(now: ZonedDateTime): List<TimePreset> =
            entries.filter { it != Tonight || now.hour < EVENING_HOUR - 1 }

        private const val EVENING_HOUR = 20
        private const val MORNING_HOUR = 9
    }
}
