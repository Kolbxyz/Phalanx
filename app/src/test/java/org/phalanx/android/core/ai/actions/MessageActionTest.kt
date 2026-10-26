package org.phalanx.android.core.ai.actions

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageActionTest {
    @Test
    fun `an event keeps its time, end and place`() = assertEquals(
        listOf(
            MessageAction.Event(
                "Dîner",
                LocalDateTime.of(2026, 10, 30, 20, 0),
                LocalDateTime.of(2026, 10, 30, 22, 30),
                allDay = false,
                place = "Café X"
            )
        ),
        actionsOf(
            """[{"type":"event","title":"Dîner","start":"2026-10-30T20:00","end":"2026-10-30T22:30",""" +
                """"minutes":null,"detail":"Café X"}]"""
        )
    )

    @Test
    fun `a day without a time is a whole-day event`() {
        val event = actionsOf(
            """[{"type":"event","title":"Anniv","start":"2026-11-02","end":null,"minutes":null,"detail":null}]"""
        ).single() as MessageAction.Event
        assertEquals(true, event.allDay)
    }

    @Test
    fun `a date that does not exist is dropped`() = assertEquals(
        emptyList<MessageAction>(),
        actionsOf(
            """[{"type":"event","title":"x","start":"2026-02-30T10:00","end":null,"minutes":null,""" +
                """"detail":null}]"""
        )
    )

    @Test
    fun `an end before the start is ignored`() {
        val event = actionsOf(
            """[{"type":"event","title":"x","start":"2026-10-30T20:00","end":"2026-10-30T19:00",""" +
                """"minutes":null,"detail":null}]"""
        ).single() as MessageAction.Event
        assertEquals(null, event.end)
    }

    @Test
    fun `items missing what they need are left out`() = assertEquals(
        listOf(MessageAction.Timer("Pâtes", 10), MessageAction.Task("Acheter du lait")),
        actionsOf(
            """[{"type":"timer","title":"Pâtes","start":null,"end":null,"minutes":10,"detail":null},""" +
                """{"type":"place","title":"Chez Léa","start":null,"end":null,"minutes":null,"detail":null},""" +
                """{"type":"task","title":"Acheter du lait","start":null,"end":null,"minutes":null,"detail":null}]"""
        )
    )

    @Test
    fun `a reply that is not json gives nothing`() = assertEquals(emptyList<MessageAction>(), actionsOf("Sure!"))
}
