package org.phalanx.android.ui.screens.catchup

import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.phalanx.android.ui.screens.room.TimePreset

class TimePresetTest {
    private fun at(hour: Int) = ZonedDateTime.of(2026, 11, 10, hour, 0, 0, 0, ZoneOffset.UTC)

    @Test
    fun `tonight is offered only before the evening starts`() {
        assertEquals(TimePreset.entries.toList(), TimePreset.available(at(10)))
        assertEquals(listOf(TimePreset.InAnHour, TimePreset.TomorrowMorning), TimePreset.available(at(19)))
    }

    @Test
    fun `presets land on the hour they name`() {
        val now = at(10)
        assertEquals(at(11).toInstant().toEpochMilli(), TimePreset.InAnHour.at(now))
        assertEquals(at(20).toInstant().toEpochMilli(), TimePreset.Tonight.at(now))
        assertEquals(at(9).plusDays(1).toInstant().toEpochMilli(), TimePreset.TomorrowMorning.at(now))
    }
}
